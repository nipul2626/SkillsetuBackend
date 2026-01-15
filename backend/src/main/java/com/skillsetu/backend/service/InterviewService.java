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
     * ✅ FIXED: Main evaluation flow with correct type handling
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

        // 3. Map DTO QAPairs to AIServiceManager QAPairs
        List<AIServiceManager.QAPair> serviceQaHistory = request.getQaHistory().stream()
                .map(dto -> new AIServiceManager.QAPair(dto.getQuestion(), dto.getAnswer()))
                .collect(java.util.stream.Collectors.toList());

        // 4. Call AI evaluation (async)
        CompletableFuture<AIServiceManager.CombinedResult> aiResultFuture =
                aiServiceManager.evaluateInterviewAsync(serviceQaHistory);

        // 5. Wait for AI result (with timeout)
        AIServiceManager.CombinedResult aiResult;
        try {
            aiResult = aiResultFuture.get(60, java.util.concurrent.TimeUnit.SECONDS); // Increased timeout
        } catch (Exception e) {
            log.error("AI evaluation timeout or error", e);
            throw new RuntimeException("Evaluation service unavailable: " + e.getMessage());
        }

        // 6. Save evaluation to database
        Evaluation evaluation = createEvaluation(interview, aiResult.getEvaluation());
        evaluationRepository.save(evaluation);

        // 7. Generate and save roadmap
        Roadmap roadmap = createRoadmap(student, interview, aiResult.getTrainingPlan());
        roadmapRepository.save(roadmap);

        // 8. Update student statistics
        updateStudentStats(student, aiResult.getEvaluation().getOverallScore());
        userRepository.save(student);

        // 9. Invalidate student cache
        invalidateStudentCache(student.getId());

        // 10. Cache the results
        cacheResults(student.getId(), evaluation, roadmap);

        // 11. Build response
        return buildInterviewResponse(interview, evaluation, roadmap, aiResult);
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

    /**
     * ✅ FIXED: Now accepts ComprehensiveEvaluation (correct type)
     */
    private Evaluation createEvaluation(Interview interview,
                                        AIServiceManager.ComprehensiveEvaluation aiEval) {
        Evaluation evaluation = new Evaluation();
        evaluation.setInterview(interview);

        // ✅ FIXED: ComprehensiveEvaluation has scoreBreakdown field
        evaluation.setTechnicalKnowledgeScore(aiEval.getScoreBreakdown().get("technicalKnowledge"));
        evaluation.setProblemSolvingScore(aiEval.getScoreBreakdown().get("problemSolving"));
        evaluation.setCommunicationScore(aiEval.getScoreBreakdown().get("communication"));
        evaluation.setDepthOfUnderstandingScore(aiEval.getScoreBreakdown().get("depthOfUnderstanding"));
        evaluation.setCoachFeedback(aiEval.getCoachFeedback());

        try {
            evaluation.setTopStrengthsJson(objectMapper.writeValueAsString(aiEval.getTopStrengths()));
            evaluation.setCriticalGapsJson(objectMapper.writeValueAsString(aiEval.getCriticalGaps()));
            evaluation.setQuestionAnalysisJson(objectMapper.writeValueAsString(aiEval.getQuestionAnalysis()));

            // ✅ ComprehensiveEvaluation doesn't have immediateActions, skip if not needed
            // Or create empty array
            evaluation.setImmediateActionsJson(objectMapper.writeValueAsString(new java.util.ArrayList<>()));
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
        int totalTasks = 0;
        if (aiPlan.getWeeklyPlan() != null) {
            totalTasks = aiPlan.getWeeklyPlan().stream()
                    .mapToInt(week -> {
                        int topicsCount = week.getTopics() != null ? week.getTopics().size() : 0;
                        int problemsCount = week.getPracticeProblems() != null ?
                                week.getPracticeProblems().size() : 0;
                        return topicsCount + problemsCount;
                    })
                    .sum();
        }
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

    /**
     * ✅ FIXED: Build response with proper DTO mapping
     */
    private InterviewResponse buildInterviewResponse(Interview interview,
                                                     Evaluation evaluation,
                                                     Roadmap roadmap,
                                                     AIServiceManager.CombinedResult aiResult) {
        InterviewResponse response = new InterviewResponse();
        response.setInterviewId(interview.getId());
        response.setOverallScore(interview.getOverallScore());
        response.setMessage("Evaluation completed successfully");

        // ✅ Map evaluation to DTO
        response.setEvaluation(mapToEvaluationDTO(aiResult.getEvaluation()));

        // ✅ Map roadmap to DTO
        response.setRoadmap(mapToRoadmapDTO(roadmap, aiResult.getTrainingPlan()));

        return response;
    }

    /**
     * ✅ NEW: Map ComprehensiveEvaluation to EvaluationDTO
     */
    private EvaluationDTO mapToEvaluationDTO(AIServiceManager.ComprehensiveEvaluation eval) {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setOverallScore(eval.getOverallScore());

        // Map score breakdown
        EvaluationDTO.ScoreBreakdown breakdown = new EvaluationDTO.ScoreBreakdown();
        breakdown.setTechnicalKnowledge(eval.getScoreBreakdown().get("technicalKnowledge"));
        breakdown.setProblemSolving(eval.getScoreBreakdown().get("problemSolving"));
        breakdown.setCommunication(eval.getScoreBreakdown().get("communication"));
        breakdown.setDepthOfUnderstanding(eval.getScoreBreakdown().get("depthOfUnderstanding"));
        dto.setScoreBreakdown(breakdown);

        // Map question analysis
        List<EvaluationDTO.QuestionAnalysis> questionAnalysisList = new java.util.ArrayList<>();
        if (eval.getQuestionAnalysis() != null) {
            for (AIServiceManager.QuestionAnalysis qa : eval.getQuestionAnalysis()) {
                EvaluationDTO.QuestionAnalysis qaDto = new EvaluationDTO.QuestionAnalysis();
                qaDto.setQuestionNumber(qa.getQuestionNumber());
                qaDto.setScore(qa.getScore());
                qaDto.setWhatYouAnswered(qa.getWhatYouAnswered());
                qaDto.setWhatWasGood(qa.getWhatWasGood());
                qaDto.setWhatWasMissing(qa.getWhatWasMissing());
                qaDto.setIdealAnswer(qa.getIdealAnswer());
                questionAnalysisList.add(qaDto);
            }
        }
        dto.setQuestionAnalysis(questionAnalysisList);

        dto.setCoachFeedback(eval.getCoachFeedback());
        dto.setTopStrengths(eval.getTopStrengths());
        dto.setCriticalGaps(eval.getCriticalGaps());

        return dto;
    }  private RoadmapDTO mapToRoadmapDTO(Roadmap roadmap, AIServiceManager.TrainingPlan plan) {
        RoadmapDTO dto = new RoadmapDTO();
        dto.setRoadmapId(roadmap.getId());
        dto.setReadinessScore(plan.getReadinessScore());
        dto.setTargetScore(plan.getTargetScore());
        dto.setTimeToTarget(plan.getTimeToTarget());

        // Map focus areas
        List<RoadmapDTO.FocusArea> focusAreasList = new java.util.ArrayList<>();
        if (plan.getFocusAreas() != null) {
            for (AIServiceManager.FocusArea fa : plan.getFocusAreas()) {
                RoadmapDTO.FocusArea faDto = new RoadmapDTO.FocusArea();
                faDto.setArea(fa.getArea());
                faDto.setPriority(fa.getPriority());
                faDto.setCurrentLevel(fa.getCurrentLevel());
                faDto.setTargetLevel(fa.getTargetLevel());
                faDto.setEstimatedHours(fa.getEstimatedHours());
                focusAreasList.add(faDto);
            }
        }
        dto.setFocusAreas(focusAreasList);

        // Map weekly plan
        List<RoadmapDTO.WeeklyPlan> weeklyPlanList = new java.util.ArrayList<>();
        if (plan.getWeeklyPlan() != null) {
            for (AIServiceManager.WeeklyPlan wp : plan.getWeeklyPlan()) {
                RoadmapDTO.WeeklyPlan wpDto = new RoadmapDTO.WeeklyPlan();
                wpDto.setWeek(wp.getWeek());
                wpDto.setTheme(wp.getTheme());
                wpDto.setStudyTime(wp.getStudyTime());
                wpDto.setTopics(wp.getTopics());
                weeklyPlanList.add(wpDto);
            }
        }
        dto.setWeeklyPlan(weeklyPlanList);

        return dto;
    }
}