package com.skillsetu.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsetu.backend.dto.*;
import com.skillsetu.backend.entity.*;
import com.skillsetu.backend.repository.*;
import com.skillsetu.backend.util.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final EvaluationRepository evaluationRepository;
    private final RoadmapRepository roadmapRepository;
    private final UserRepository userRepository;
    private final AIServiceManager aiServiceManager;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    /**
     * Main evaluation flow with caching
     */
    @Transactional
    public InterviewResponse evaluateAndGenerateRoadmap(InterviewRequest request) {
        log.info("Starting evaluation for student: {}", request.getStudentId());

        // 1. Validate student exists
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // 2. Save interview record first
        Interview interview = createInterviewRecord(student, request);
        interview = interviewRepository.save(interview);

        // 3. Call AI evaluation (async for heavy processing)
        // 3. Call AI evaluation (Fixing the Type Mismatch)
// Map DTO QAPairs to Service QAPairs
        List<AIServiceManager.QAPair> serviceQaHistory = request.getQaHistory().stream()
                .map(dto -> new AIServiceManager.QAPair(dto.getQuestion(), dto.getAnswer()))
                .collect(java.util.stream.Collectors.toList());

// Now pass the correctly typed list
        CompletableFuture<AIServiceManager.CombinedResult> aiResultFuture =
                aiServiceManager.evaluateInterviewAsync(serviceQaHistory);

        // 4. Wait for AI result (with timeout)
        AIServiceManager.CombinedResult aiResult;
        try {
            aiResult = aiResultFuture.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("AI evaluation timeout or error", e);
            throw new RuntimeException("Evaluation service unavailable");
        }

        // 5. Save evaluation to database
        Evaluation evaluation = createEvaluation(interview, aiResult.getEvaluation());
        evaluationRepository.save(evaluation);

        // 6. Generate and save roadmap
        Roadmap roadmap = createRoadmap(student, interview, aiResult.getTrainingPlan());
        roadmapRepository.save(roadmap);

        // 7. Update student statistics
        updateStudentStats(student, aiResult.getEvaluation().getOverallScore());
        userRepository.save(student);

        // 8. Invalidate student cache
        invalidateStudentCache(student.getId());

        // 9. Cache the results
        cacheResults(student.getId(), evaluation, roadmap);

        // 10. Build response
        return buildInterviewResponse(interview, evaluation, roadmap);
    }

    /**
     * Get student interviews with caching
     */
    public Page<Interview> getStudentInterviews(Long studentId, int page, int size) {
        return interviewRepository.findByStudentIdOrderByCreatedAtDesc(
                studentId, PageRequest.of(page, size));
    }

    /**
     * Get interview by ID
     */
    public Interview getInterviewById(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));
    }

    // ==================== HELPER METHODS ====================

    private Interview createInterviewRecord(User student, InterviewRequest request) {
        Interview interview = new Interview();
        interview.setStudent(student);
        interview.setInterviewType(request.getInterviewType());
        interview.setJobRole(request.getJobRole());
        interview.setTotalTime(request.getTotalTime());
        interview.setIsRetake(request.getIsRetake());
        interview.setOverallScore(0.0); // Will be updated after evaluation

        try {
            interview.setQaHistoryJson(objectMapper.writeValueAsString(request.getQaHistory()));
        } catch (Exception e) {
            log.error("Error serializing QA history", e);
        }

        return interview;
    }

    private Evaluation createEvaluation(Interview interview,
                                        AIServiceManager.EvaluationResult aiEval) {
        Evaluation evaluation = new Evaluation();
        evaluation.setInterview(interview);
        evaluation.setTechnicalKnowledgeScore(aiEval.getScoreBreakdown().get("technicalKnowledge"));
        evaluation.setProblemSolvingScore(aiEval.getScoreBreakdown().get("problemSolving"));
        evaluation.setCommunicationScore(aiEval.getScoreBreakdown().get("communication"));
        evaluation.setDepthOfUnderstandingScore(aiEval.getScoreBreakdown().get("depthOfUnderstanding"));
        evaluation.setCoachFeedback(aiEval.getCoachFeedback());

        try {
            evaluation.setTopStrengthsJson(objectMapper.writeValueAsString(aiEval.getTopStrengths()));
            evaluation.setCriticalGapsJson(objectMapper.writeValueAsString(aiEval.getCriticalGaps()));
            evaluation.setQuestionAnalysisJson(objectMapper.writeValueAsString(aiEval.getQuestionAnalysis()));
            evaluation.setImmediateActionsJson(objectMapper.writeValueAsString(aiEval.getImmediateActions()));
        } catch (Exception e) {
            log.error("Error serializing evaluation data", e);
        }

        // Update interview overall score
        interview.setOverallScore(aiEval.getOverallScore());

        return evaluation;
    }

    private Roadmap createRoadmap(User student, Interview interview,
                                  AIServiceManager.TrainingPlan aiPlan) {
        Roadmap roadmap = new Roadmap();
        roadmap.setStudent(student);
        roadmap.setInterview(interview);
        roadmap.setJobRole(interview.getJobRole());
        roadmap.setInterviewType(interview.getInterviewType());
        roadmap.setReadinessScore(aiPlan.getReadinessScore());
        roadmap.setTargetScore(aiPlan.getTargetScore());
        roadmap.setTimeToTarget(aiPlan.getTimeToTarget());
        roadmap.setStartDate(LocalDate.now());
        roadmap.setTargetCompletionDate(LocalDate.now().plusDays(30));
        roadmap.setStatus(Roadmap.RoadmapStatus.ACTIVE);

        // Calculate total tasks from weekly plan
        int totalTasks = aiPlan.getWeeklyPlan().stream()
                .mapToInt(week -> week.getTopics().size() + week.getPracticeProblems().size())
                .sum();
        roadmap.setTotalTasksCount(totalTasks);

        try {
            roadmap.setFocusAreasJson(objectMapper.writeValueAsString(aiPlan.getFocusAreas()));
            roadmap.setWeeklyPlanJson(objectMapper.writeValueAsString(aiPlan.getWeeklyPlan()));
            roadmap.setMilestonesJson(objectMapper.writeValueAsString(aiPlan.getMilestones()));
        } catch (Exception e) {
            log.error("Error serializing roadmap data", e);
        }

        return roadmap;
    }

    private void updateStudentStats(User student, Double overallScore) {
        student.setTotalInterviewsTaken(student.getTotalInterviewsTaken() + 1);

        // Calculate new average
        double currentTotal = student.getAverageScore() * (student.getTotalInterviewsTaken() - 1);
        double newAverage = (currentTotal + overallScore) / student.getTotalInterviewsTaken();
        student.setAverageScore(newAverage);

        // Update readiness score (weighted: 70% new, 30% historical)
        double newReadiness = (overallScore * 10 * 0.7) + (student.getPlacementReadinessScore() * 0.3);
        student.setPlacementReadinessScore(newReadiness);
    }

    private void invalidateStudentCache(Long studentId) {
        cacheService.invalidate(CacheKeyBuilder.readinessScoreKey(studentId));
        cacheService.invalidate(CacheKeyBuilder.activeRoadmapKey(studentId));
        cacheService.invalidatePattern(CacheKeyBuilder.studentPattern(studentId));
    }

    private void cacheResults(Long studentId, Evaluation evaluation, Roadmap roadmap) {
        // Cache readiness score
        cacheService.put(
                CacheKeyBuilder.readinessScoreKey(studentId),
                roadmap.getReadinessScore(),
                Duration.ofHours(1)
        );

        // Cache active roadmap
        cacheService.put(
                CacheKeyBuilder.activeRoadmapKey(studentId),
                roadmap,
                Duration.ofMinutes(30)
        );
    }

    private InterviewResponse buildInterviewResponse(Interview interview,
                                                     Evaluation evaluation,
                                                     Roadmap roadmap) {
        InterviewResponse response = new InterviewResponse();
        response.setInterviewId(interview.getId());
        response.setOverallScore(interview.getOverallScore());
        response.setMessage("Evaluation completed successfully");

        // Convert entities to DTOs (implement mapping logic)
        // response.setEvaluation(mapToEvaluationDTO(evaluation));
        // response.setRoadmap(mapToRoadmapDTO(roadmap));

        return response;
    }
}