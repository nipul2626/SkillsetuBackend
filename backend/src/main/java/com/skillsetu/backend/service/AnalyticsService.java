package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.CollegeAnalyticsDTO;
import com.skillsetu.backend.dto.StudentPerformanceDTO;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.repository.*;
import com.skillsetu.backend.util.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final RoadmapRepository roadmapRepository;
    private final CollegeRepository collegeRepository;
    private final CacheService cacheService;

    /**
     * Get comprehensive college analytics (cached for 15 minutes)
     */
    @Cacheable(value = "college_analytics", key = "#collegeId")
    public CollegeAnalyticsDTO getCollegeAnalytics(Long collegeId) {
        log.info("Computing analytics for college: {}", collegeId);

        String cacheKey = CacheKeyBuilder.collegeAnalyticsKey(collegeId);

        return cacheService.getOrCompute(
                cacheKey,
                CollegeAnalyticsDTO.class,
                () -> computeCollegeAnalytics(collegeId),
                Duration.ofMinutes(15)
        );
    }

    private CollegeAnalyticsDTO computeCollegeAnalytics(Long collegeId) {
        CollegeAnalyticsDTO analytics = new CollegeAnalyticsDTO();

        // Basic counts
        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.STUDENT
        );
        analytics.setTotalStudents((long) students.size());

        Long totalInterviews = interviewRepository.countByCollegeId(collegeId);
        analytics.setTotalInterviews(totalInterviews);

        Long activeRoadmaps = roadmapRepository.countActiveRoadmapsByCollege(collegeId);
        analytics.setActiveRoadmaps(activeRoadmaps);

        // Average scores
        Double avgInterviewScore = interviewRepository.getAverageScoreByCollege(collegeId);
        analytics.setAverageInterviewScore(avgInterviewScore != null ? avgInterviewScore : 0.0);

        Double avgReadiness = userRepository.getAverageReadinessScoreByCollege(collegeId);
        analytics.setAverageReadinessScore(avgReadiness != null ? avgReadiness : 0.0);

        // Top performers
        List<User> topPerformers = students.stream()
                .sorted(Comparator.comparing(User::getPlacementReadinessScore).reversed())
                .limit(10)
                .collect(Collectors.toList());
        analytics.setTopPerformers(mapToStudentPerformanceDTO(topPerformers));

        // Distribution by job role
        Map<String, Long> roleDistribution = computeRoleDistribution(collegeId);
        analytics.setJobRoleDistribution(roleDistribution);

        // Recent activity (last 7 days)
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        analytics.setRecentInterviewsCount(
                interviewRepository.findByCollegeId(collegeId, Pageable.unpaged())
                        .stream()
                        .filter(i -> i.getCreatedAt().isAfter(weekAgo))
                        .count()
        );

        return analytics;
    }

    /**
     * Get student performance details
     */
    public StudentPerformanceDTO getStudentPerformance(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        StudentPerformanceDTO performance = new StudentPerformanceDTO();
        performance.setStudentId(studentId);
        performance.setFullName(student.getFullName());
        performance.setEmail(student.getEmail());
        performance.setPlacementReadinessScore(student.getPlacementReadinessScore());
        performance.setTotalInterviews(student.getTotalInterviewsTaken());
        performance.setAverageScore(student.getAverageScore());

        // Recent interviews
        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
        var recentInterviews = interviewRepository.findRecentInterviews(studentId, monthAgo);
        performance.setRecentInterviewsCount(recentInterviews.size());

        // Score trend
        List<Double> scoreTrend = recentInterviews.stream()
                .sorted(Comparator.comparing(i -> i.getCreatedAt()))
                .map(i -> i.getOverallScore())
                .collect(Collectors.toList());
        performance.setScoreTrend(scoreTrend);

        return performance;
    }

    /**
     * Get students needing attention (low scores)
     */
    public List<StudentPerformanceDTO> getStudentsNeedingAttention(Long collegeId, int limit) {
        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.STUDENT
        );

        return students.stream()
                .filter(s -> s.getPlacementReadinessScore() < 60.0)
                .sorted(Comparator.comparing(User::getPlacementReadinessScore))
                .limit(limit)
                .map(this::mapToBasicPerformanceDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get dashboard statistics
     */
    public Map<String, Object> getDashboardStats(Long collegeId) {
        Map<String, Object> stats = new HashMap<>();

        CollegeAnalyticsDTO analytics = getCollegeAnalytics(collegeId);

        stats.put("totalStudents", analytics.getTotalStudents());
        stats.put("totalInterviews", analytics.getTotalInterviews());
        stats.put("activeRoadmaps", analytics.getActiveRoadmaps());
        stats.put("avgReadinessScore", analytics.getAverageReadinessScore());
        stats.put("avgInterviewScore", analytics.getAverageInterviewScore());
        stats.put("recentActivity", analytics.getRecentInterviewsCount());

        // Students by readiness level
        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.STUDENT
        );

        long excellent = students.stream().filter(s -> s.getPlacementReadinessScore() >= 80).count();
        long good = students.stream().filter(s -> s.getPlacementReadinessScore() >= 60 && s.getPlacementReadinessScore() < 80).count();
        long needsWork = students.stream().filter(s -> s.getPlacementReadinessScore() < 60).count();

        Map<String, Long> readinessBreakdown = new HashMap<>();
        readinessBreakdown.put("excellent", excellent);
        readinessBreakdown.put("good", good);
        readinessBreakdown.put("needsWork", needsWork);

        stats.put("readinessBreakdown", readinessBreakdown);

        return stats;
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Long> computeRoleDistribution(Long collegeId) {
        var interviews = interviewRepository.findByCollegeId(collegeId, Pageable.unpaged());

        return interviews.stream()
                .map(i -> i.getJobRole())
                .collect(Collectors.groupingBy(
                        role -> role,
                        Collectors.counting()
                ));
    }

    private List<StudentPerformanceDTO> mapToStudentPerformanceDTO(List<User> students) {
        return students.stream()
                .map(this::mapToBasicPerformanceDTO)
                .collect(Collectors.toList());
    }

    private StudentPerformanceDTO mapToBasicPerformanceDTO(User student) {
        StudentPerformanceDTO dto = new StudentPerformanceDTO();
        dto.setStudentId(student.getId());
        dto.setFullName(student.getFullName());
        dto.setEmail(student.getEmail());
        dto.setPlacementReadinessScore(student.getPlacementReadinessScore());
        dto.setTotalInterviews(student.getTotalInterviewsTaken());
        dto.setAverageScore(student.getAverageScore());
        return dto;
    }
}
