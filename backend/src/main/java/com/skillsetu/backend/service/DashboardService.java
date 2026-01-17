package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.DashboardResponseDTO;
import com.skillsetu.backend.entity.*;
import com.skillsetu.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InterviewRepository interviewRepository;
    private final EvaluationRepository evaluationRepository;
    private final UserRepository userRepository;

    public DashboardResponseDTO getStudentDashboard(Long studentId) {

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<Interview> interviews = interviewRepository.findByStudentId(studentId);

        DashboardResponseDTO dto = new DashboardResponseDTO();
        dto.setStudentId(studentId);
        dto.setStudentName(student.getFullName());

        dto.setTotalInterviews(interviews.size());

        double avg = interviews.stream()
                .mapToDouble(Interview::getOverallScore)
                .average()
                .orElse(0);

        dto.setAverageScore(avg);
        dto.setReadinessScore((int) Math.min(100, avg * 10));

        // Weekly trend
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recent = interviewRepository.findRecentInterviews(studentId, weekAgo).size();
        dto.setWeeklyTrend((int) recent * 5); // simple heuristic

        // Skills (from latest evaluation)
        interviews.stream()
                .filter(i -> i.getEvaluation() != null)
                .findFirst()
                .ifPresent(i -> dto.setSkills(List.of(
                        skill("Technical Knowledge", i.getEvaluation().getTechnicalKnowledgeScore()),
                        skill("Problem Solving", i.getEvaluation().getProblemSolvingScore()),
                        skill("Communication", i.getEvaluation().getCommunicationScore()),
                        skill("Depth", i.getEvaluation().getDepthOfUnderstandingScore())
                )));

        // Recent activities
        dto.setRecentActivities(
                interviews.stream()
                        .limit(5)
                        .map(i -> {
                            DashboardResponseDTO.ActivityDTO a = new DashboardResponseDTO.ActivityDTO();
                            a.setTitle("Mock Interview - " + i.getJobRole());
                            a.setScore((int) (i.getOverallScore() * 10));
                            a.setTimestamp(i.getCreatedAt().toString());
                            return a;
                        }).toList()
        );

        return dto;
    }

    private DashboardResponseDTO.SkillDTO skill(String name, Double score) {
        DashboardResponseDTO.SkillDTO s = new DashboardResponseDTO.SkillDTO();
        s.setName(name);
        s.setPercentage(score == null ? 0 : (int) (score * 10));
        return s;
    }
}
