package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.DashboardResponseDTO;
import com.skillsetu.backend.entity.*;
import com.skillsetu.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final RoadmapRepository roadmapRepository;

    /**
     * Generate comprehensive dashboard data from real database records
     */
    @Transactional(readOnly = true)
    public DashboardResponseDTO getStudentDashboard(Long studentId) {
        log.info("Fetching dashboard data for student ID: {}", studentId);

        // Get student
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

        // Get all interviews
        List<Interview> allInterviews = interviewRepository.findByStudentId(studentId);
        log.info("Found {} total interviews for student {}", allInterviews.size(), studentId);

        DashboardResponseDTO dto = new DashboardResponseDTO();
        dto.setStudentId(studentId);
        dto.setStudentName(student.getFullName());

        // Calculate stats from real data
        dto.setTotalInterviews(allInterviews.size());

        // Calculate average score (scores are 0-10, we store them as such)
        double avgScore = allInterviews.stream()
                .mapToDouble(Interview::getOverallScore)
                .average()
                .orElse(0.0);
        dto.setAverageScore(avgScore);

        // Readiness score: use user's placement readiness or calculate from avg
        Double userReadiness = student.getPlacementReadinessScore();
        if (userReadiness != null && userReadiness > 0) {
            dto.setReadinessScore(userReadiness.intValue());
        } else {
            // Calculate as percentage: avgScore (0-10) * 10 = 0-100
            dto.setReadinessScore((int) Math.min(100, Math.max(0, avgScore * 10)));
        }

        // Weekly trend: count interviews in last 7 days
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recentCount = allInterviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(weekAgo))
                .count();
        dto.setWeeklyTrend((int) recentCount);

        // Skills breakdown from latest evaluation
        dto.setSkills(extractSkillsFromLatestInterview(allInterviews));

        // Recent activities: last 5 interviews with their scores
        dto.setRecentActivities(buildRecentActivities(allInterviews));

        // Roadmap count
        long roadmapCount = roadmapRepository.countByStudentId(studentId);
        dto.setRoadmapCount((int) roadmapCount);

        log.info("Dashboard generated: {} interviews, {}% readiness, {} roadmaps",
                dto.getTotalInterviews(), dto.getReadinessScore(), dto.getRoadmapCount());

        return dto;
    }

    /**
     * Extract skill percentages from the most recent interview evaluation
     */
    private List<DashboardResponseDTO.SkillDTO> extractSkillsFromLatestInterview(List<Interview> interviews) {
        List<DashboardResponseDTO.SkillDTO> skills = new ArrayList<>();

        if (interviews.isEmpty()) {
            // Return default empty skills
            return List.of(
                    createSkill("Technical Knowledge", 0),
                    createSkill("Problem Solving", 0),
                    createSkill("Communication", 0)
            );
        }

        // Get most recent interview with evaluation
        Interview latest = interviews.stream()
                .filter(i -> i.getEvaluation() != null)
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElse(interviews.get(0));

        Evaluation eval = latest.getEvaluation();
        if (eval != null) {
            // Convert scores (0-10) to percentages (0-100)
            skills.add(createSkill("Technical Knowledge",
                    eval.getTechnicalKnowledgeScore() != null ? (int)(eval.getTechnicalKnowledgeScore() * 10) : 0));
            skills.add(createSkill("Problem Solving",
                    eval.getProblemSolvingScore() != null ? (int)(eval.getProblemSolvingScore() * 10) : 0));
            skills.add(createSkill("Communication",
                    eval.getCommunicationScore() != null ? (int)(eval.getCommunicationScore() * 10) : 0));
            skills.add(createSkill("Depth of Understanding",
                    eval.getDepthOfUnderstandingScore() != null ? (int)(eval.getDepthOfUnderstandingScore() * 10) : 0));
        } else {
            // Use overall score as fallback
            int percentage = (int)(latest.getOverallScore() * 10);
            skills.add(createSkill("Overall Performance", percentage));
        }

        return skills;
    }

    /**
     * Build recent activity list from interviews
     */
    private List<DashboardResponseDTO.ActivityDTO> buildRecentActivities(List<Interview> interviews) {
        if (interviews.isEmpty()) {
            return new ArrayList<>();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");

        return interviews.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // Latest first
                .limit(5)
                .map(interview -> {
                    DashboardResponseDTO.ActivityDTO activity = new DashboardResponseDTO.ActivityDTO();

                    // Title: "Technical Interview - Software Engineer"
                    String title = formatInterviewType(interview.getInterviewType()) + " - " + interview.getJobRole();
                    activity.setTitle(title);

                    // Score as percentage (0-100)
                    activity.setScore((int)(interview.getOverallScore() * 10));

                    // Formatted timestamp
                    activity.setTimestamp(interview.getCreatedAt().format(formatter));

                    // Interview ID for future navigation
                    activity.setInterviewId(interview.getId());

                    return activity;
                })
                .collect(Collectors.toList());
    }

    /**
     * Format interview type for display
     */
    private String formatInterviewType(String type) {
        if (type == null) return "Interview";

        switch (type.toLowerCase()) {
            case "technical": return "Technical Interview";
            case "hr": return "HR Interview";
            case "aptitude": return "Aptitude Test";
            case "mixed": return "Mixed Interview";
            default: return type + " Interview";
        }
    }

    /**
     * Helper to create skill DTO
     */
    private DashboardResponseDTO.SkillDTO createSkill(String name, int percentage) {
        DashboardResponseDTO.SkillDTO skill = new DashboardResponseDTO.SkillDTO();
        skill.setName(name);
        skill.setPercentage(Math.min(100, Math.max(0, percentage)));
        return skill;
    }
}