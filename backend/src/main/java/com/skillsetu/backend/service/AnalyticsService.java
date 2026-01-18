package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.*;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.entity.Interview;
import com.skillsetu.backend.entity.Roadmap;
import com.skillsetu.backend.repository.*;
import com.skillsetu.backend.util.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.util.HashMap;
import java.util.Map;



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

    // ==================== EXISTING METHODS ====================

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

        List<User> students = userRepository.findByCollegeIdAndRole(collegeId, User.UserRole.ROLE_STUDENT);
        analytics.setTotalStudents((long) students.size());

        Long totalInterviews = interviewRepository.countByCollegeId(collegeId);
        analytics.setTotalInterviews(totalInterviews);

        Long activeRoadmaps = roadmapRepository.countActiveRoadmapsByCollege(collegeId);
        analytics.setActiveRoadmaps(activeRoadmaps);

        Double avgInterviewScore = interviewRepository.getAverageScoreByCollege(collegeId);
        analytics.setAverageInterviewScore(avgInterviewScore != null ? avgInterviewScore : 0.0);

        Double avgReadiness = userRepository.getAverageReadinessScoreByCollege(collegeId);
        analytics.setAverageReadinessScore(avgReadiness != null ? avgReadiness : 0.0);

        List<User> topPerformers = students.stream()
                .sorted(Comparator.comparing(User::getPlacementReadinessScore).reversed())
                .limit(10)
                .collect(Collectors.toList());
        analytics.setTopPerformers(mapToStudentPerformanceDTO(topPerformers));

        Map<String, Long> roleDistribution = computeRoleDistribution(collegeId);
        analytics.setJobRoleDistribution(roleDistribution);

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        analytics.setRecentInterviewsCount(
                interviewRepository.findByCollegeId(collegeId, Pageable.unpaged())
                        .stream()
                        .filter(i -> i.getCreatedAt().isAfter(weekAgo))
                        .count()
        );

        return analytics;
    }

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

        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
        var recentInterviews = interviewRepository.findRecentInterviews(studentId, monthAgo);
        performance.setRecentInterviewsCount(recentInterviews.size());

        List<Double> scoreTrend = recentInterviews.stream()
                .sorted(Comparator.comparing(i -> i.getCreatedAt()))
                .map(i -> i.getOverallScore())
                .collect(Collectors.toList());
        performance.setScoreTrend(scoreTrend);

        return performance;
    }

    public List<StudentPerformanceDTO> getStudentsNeedingAttention(Long collegeId, int limit) {
        List<User> students = userRepository.findByCollegeIdAndRole(collegeId, User.UserRole.ROLE_STUDENT);

        return students.stream()
                .filter(s -> s.getPlacementReadinessScore() < 60.0)
                .sorted(Comparator.comparing(User::getPlacementReadinessScore))
                .limit(limit)
                .map(this::mapToBasicPerformanceDTO)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardStats(Long collegeId) {
        Map<String, Object> stats = new HashMap<>();
        CollegeAnalyticsDTO analytics = getCollegeAnalytics(collegeId);

        stats.put("totalStudents", analytics.getTotalStudents());
        stats.put("totalInterviews", analytics.getTotalInterviews());
        stats.put("activeRoadmaps", analytics.getActiveRoadmaps());
        stats.put("avgReadinessScore", analytics.getAverageReadinessScore());
        stats.put("avgInterviewScore", analytics.getAverageInterviewScore());
        stats.put("recentActivity", analytics.getRecentInterviewsCount());

        List<User> students = userRepository.findByCollegeIdAndRole(collegeId, User.UserRole.ROLE_STUDENT);
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

    // ==================== TPO DASHBOARD METHODS ====================

    /**
     * Get filtered students with pagination
     */
    public Map<String, Object> getFilteredStudents(
            Long collegeId,
            StudentFilterRequest filterRequest) {

        List<StudentPerformanceDTO> students =
                userRepository.findByCollegeIdAndRole(
                                collegeId,
                                User.UserRole.ROLE_STUDENT
                        )
                        .stream()
                        .map(this::mapToStudentDTO)
                        .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("students", students);
        response.put("total", students.size());

        return response;
    }
    /**
     * 🔹 Maps User entity to StudentPerformanceDTO
     * Used by TPO student list
     */
    private StudentPerformanceDTO mapToStudentDTO(User student) {

        StudentPerformanceDTO dto = new StudentPerformanceDTO();

        dto.setStudentId(student.getId());
        dto.setFullName(student.getFullName());
        dto.setEmail(student.getEmail());

        dto.setPlacementReadinessScore(
                student.getPlacementReadinessScore() != null
                        ? student.getPlacementReadinessScore()
                        : 0.0
        );

        dto.setTotalInterviews(
                student.getTotalInterviewsTaken() != null
                        ? student.getTotalInterviewsTaken()
                        : 0
        );

        dto.setAverageScore(
                student.getAverageScore() != null
                        ? student.getAverageScore()
                        : 0.0
        );

        return dto;
    }


    private boolean matchesFilter(
            StudentPerformanceDTO student,
            StudentFilterRequest filter) {

        if (filter.getSearchQuery() != null &&
                !student.getFullName()
                        .toLowerCase()
                        .contains(filter.getSearchQuery().toLowerCase())) {
            return false;
        }

        if (filter.getMinReadinessScore() != null &&
                student.getPlacementReadinessScore() < filter.getMinReadinessScore()) {
            return false;
        }

        if (filter.getMaxReadinessScore() != null &&
                student.getPlacementReadinessScore() > filter.getMaxReadinessScore()) {
            return false;
        }

        return true;
    }


    private boolean matchesFilters(StudentPerformanceDTO student, String readinessLevel,
                                   String jobRole, String searchQuery) {
        if (readinessLevel != null && !readinessLevel.equals("all")) {
            String studentLevel = getReadinessLevel(student.getPlacementReadinessScore());
            if (!studentLevel.equalsIgnoreCase(readinessLevel)) {
                return false;
            }
        }

        if (searchQuery != null && !searchQuery.isEmpty()) {
            String query = searchQuery.toLowerCase();
            return student.getFullName().toLowerCase().contains(query) ||
                    student.getEmail().toLowerCase().contains(query);
        }

        return true;
    }
    /**
     * 🆕 Get branch-wise statistics
     */
    public Map<String, Object> getBranchWiseStats(Long collegeId) {
        Map<String, Object> stats = new HashMap<>();

        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.ROLE_STUDENT);

        // Group by branch
        Map<String, List<User>> byBranch = students.stream()
                .filter(s -> s.getBranch() != null)
                .collect(Collectors.groupingBy(User::getBranch));

        // Calculate stats per branch
        Map<String, Map<String, Object>> branchStats = new HashMap<>();

        for (Map.Entry<String, List<User>> entry : byBranch.entrySet()) {
            String branch = entry.getKey();
            List<User> branchStudents = entry.getValue();

            Map<String, Object> branchData = new HashMap<>();
            branchData.put("totalStudents", branchStudents.size());
            branchData.put("avgReadiness",
                    branchStudents.stream()
                            .mapToDouble(User::getPlacementReadinessScore)
                            .average()
                            .orElse(0.0));
            branchData.put("totalInterviews",
                    branchStudents.stream()
                            .mapToInt(User::getTotalInterviewsTaken)
                            .sum());
            branchData.put("avgScore",
                    branchStudents.stream()
                            .mapToDouble(User::getAverageScore)
                            .average()
                            .orElse(0.0));

            branchStats.put(branch, branchData);
        }

        stats.put("branches", branchStats);
        stats.put("totalBranches", branchStats.size());

        return stats;
    }
    private String getReadinessLevel(Double score) {
        if (score >= 80) return "Excellent";
        if (score >= 60) return "Good";
        if (score >= 40) return "Fair";
        return "Needs Work";
    }

    /**
     * Get performance trend data for charts
     */
    public Map<String, Object> getPerformanceTrend(Long collegeId, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        List<Interview> interviews = interviewRepository.findByCollegeId(
                        collegeId, PageRequest.of(0, 1000))
                .stream()
                .filter(i -> i.getCreatedAt().isAfter(startDate))
                .collect(Collectors.toList());

        Map<String, Object> trend = new HashMap<>();

        // Group by date
        Map<String, List<Interview>> byDate = interviews.stream()
                .collect(Collectors.groupingBy(i ->
                        i.getCreatedAt().toLocalDate().toString()));

        List<Map<String, Object>> dailyData = new ArrayList<>();
        byDate.forEach((date, dayInterviews) -> {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date);
            dayData.put("count", dayInterviews.size());
            dayData.put("avgScore", dayInterviews.stream()
                    .mapToDouble(Interview::getOverallScore)
                    .average()
                    .orElse(0.0));
            dailyData.add(dayData);
        });

        trend.put("dailyData", dailyData);
        trend.put("totalInterviews", interviews.size());
        trend.put("averageScore", interviews.stream()
                .mapToDouble(Interview::getOverallScore)
                .average()
                .orElse(0.0));

        return trend;
    }

    /**
     * Get skill gap analysis across all students
     */

    public SkillGapDTO getSkillGaps(Long collegeId, String jobRole, int limit) {

        SkillGapDTO dto = new SkillGapDTO();

        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.ROLE_STUDENT);

        // ================= Top Deficiencies =================
        Map<String, Integer> deficiencyCount = new HashMap<>();

        for (User student : students) {
            List<Interview> interviews = interviewRepository.findRecentInterviews(
                    student.getId(), LocalDateTime.now().minusMonths(1));

            for (Interview interview : interviews) {
                if (interview.getOverallScore() < 6.0) {
                    String skill = interview.getJobRole(); // placeholder
                    deficiencyCount.put(skill,
                            deficiencyCount.getOrDefault(skill, 0) + 1);
                }
            }
        }

        List<SkillGapDTO.SkillDeficiency> deficiencies =
                deficiencyCount.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(limit)
                        .map(e -> {
                            SkillGapDTO.SkillDeficiency d =
                                    new SkillGapDTO.SkillDeficiency();
                            d.setSkillName(e.getKey());
                            d.setStudentsLacking(e.getValue());
                            d.setPercentageLacking(
                                    (e.getValue() * 100.0) / students.size());
                            d.setSeverity(
                                    e.getValue() > 20 ? "Critical" :
                                            e.getValue() > 10 ? "High" : "Medium");
                            return d;
                        })
                        .collect(Collectors.toList());

        dto.setTopDeficiencies(deficiencies);

        // ================= Industry Demand (Mock) =================
        Map<String, SkillGapDTO.IndustryDemand> industryDemand = new HashMap<>();

        for (SkillGapDTO.SkillDeficiency d : deficiencies) {
            SkillGapDTO.IndustryDemand id = new SkillGapDTO.IndustryDemand();
            id.setSkillName(d.getSkillName());
            id.setDemandCount(d.getStudentsLacking() + 10);
            id.setSupplyCount(students.size() - d.getStudentsLacking());
            id.setGapPercentage(
                    ((double) id.getDemandCount() / students.size()) * 100
            );
            industryDemand.put(d.getSkillName(), id);
        }

        dto.setIndustryDemand(industryDemand);

        // ================= Training Recommendations =================
        List<SkillGapDTO.TrainingRecommendation> recommendations =
                deficiencies.stream()
                        .map(d -> {
                            SkillGapDTO.TrainingRecommendation tr =
                                    new SkillGapDTO.TrainingRecommendation();
                            tr.setSkillName(d.getSkillName());
                            tr.setPriority(d.getSeverity().equals("Critical") ? 1 : 3);
                            tr.setEstimatedStudents(d.getStudentsLacking());
                            tr.setSuggestedDuration("6 weeks");
                            tr.setReason("High failure rate in recent interviews");
                            return tr;
                        })
                        .collect(Collectors.toList());

        dto.setRecommendations(recommendations);

        return dto;
    }




    public TrendDataDTO getTrends(Long collegeId, int days) {

        TrendDataDTO dto = new TrendDataDTO();

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        List<Interview> interviews = interviewRepository
                .findByCollegeId(collegeId, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(i -> i.getCreatedAt().isAfter(startDate))
                .collect(Collectors.toList());

        // ================= Monthly Trends =================
        Map<String, List<Interview>> byMonth = interviews.stream()
                .collect(Collectors.groupingBy(i ->
                        i.getCreatedAt().getMonth().name() + " " + i.getCreatedAt().getYear()
                ));

        List<TrendDataDTO.MonthlyTrend> monthlyTrends = new ArrayList<>();

        for (Map.Entry<String, List<Interview>> entry : byMonth.entrySet()) {
            List<Interview> monthInterviews = entry.getValue();

            TrendDataDTO.MonthlyTrend mt = new TrendDataDTO.MonthlyTrend();
            mt.setMonth(entry.getKey());
            mt.setTotalInterviews(monthInterviews.size());
            mt.setAverageScore(
                    monthInterviews.stream()
                            .mapToDouble(Interview::getOverallScore)
                            .average()
                            .orElse(0.0)
            );

            mt.setStudentsImproved(
                    (int) monthInterviews.stream()
                            .map(i -> i.getStudent().getId())
                            .distinct()
                            .count()
            );

            mt.setAvgReadinessScore(
                    monthInterviews.stream()
                            .map(i -> i.getStudent().getPlacementReadinessScore())
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0.0)
            );

            monthlyTrends.add(mt);
        }

        dto.setMonthlyTrends(monthlyTrends);

        // ================= Batch Comparison =================
        TrendDataDTO.ComparisonData comparison = new TrendDataDTO.ComparisonData();

        comparison.setCurrentBatch("2024");
        comparison.setPreviousBatch("2023");

        double currentAvg = interviews.stream()
                .mapToDouble(Interview::getOverallScore)
                .average()
                .orElse(0.0);

        double previousAvg = currentAvg * 0.92; // mock baseline (replace later)

        comparison.setCurrentAvgScore(currentAvg);
        comparison.setPreviousAvgScore(previousAvg);

        comparison.setImprovement(
                previousAvg == 0 ? 0 :
                        ((currentAvg - previousAvg) / previousAvg) * 100
        );

        dto.setBatchComparison(comparison);

        return dto;
    }



    public Map<String, Object> getSkillGapAnalysis(Long collegeId) {
        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.ROLE_STUDENT);

        Map<String, Object> analysis = new HashMap<>();

        // Analyze common weaknesses from recent interviews
        Map<String, Integer> weaknessCount = new HashMap<>();

        for (User student : students) {
            List<Interview> recentInterviews = interviewRepository.findRecentInterviews(
                    student.getId(), LocalDateTime.now().minusMonths(1));

            for (Interview interview : recentInterviews) {
                if (interview.getEvaluation() != null) {
                    // Parse critical gaps from JSON if stored
                    // This is a simplified version
                    if (interview.getOverallScore() < 6.0) {
                        String jobRole = interview.getJobRole();
                        weaknessCount.put(jobRole, weaknessCount.getOrDefault(jobRole, 0) + 1);
                    }
                }
            }
        }

        List<Map<String, Object>> topGaps = weaknessCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> gap = new HashMap<>();
                    gap.put("skill", e.getKey());
                    gap.put("count", e.getValue());
                    gap.put("percentage", (e.getValue() * 100.0) / students.size());
                    return gap;
                })
                .collect(Collectors.toList());

        analysis.put("topSkillGaps", topGaps);
        analysis.put("studentsAnalyzed", students.size());

        return analysis;
    }

    /**
     * Get job role distribution
     */
    public Map<String, Object> getJobRoleAnalytics(Long collegeId) {
        Map<String, Object> analytics = new HashMap<>();

        List<Interview> interviews = interviewRepository.findByCollegeId(
                collegeId, Pageable.unpaged()).getContent();

        Map<String, Long> roleDistribution = interviews.stream()
                .collect(Collectors.groupingBy(
                        Interview::getJobRole,
                        Collectors.counting()));

        Map<String, Double> roleAverages = new HashMap<>();
        for (String role : roleDistribution.keySet()) {
            double avg = interviews.stream()
                    .filter(i -> i.getJobRole().equals(role))
                    .mapToDouble(Interview::getOverallScore)
                    .average()
                    .orElse(0.0);
            roleAverages.put(role, avg);
        }

        analytics.put("distribution", roleDistribution);
        analytics.put("averageScores", roleAverages);
        analytics.put("totalRoles", roleDistribution.size());

        return analytics;
    }

    /**
     * Get student comparison data
     */
    public Map<String, Object> compareStudents(List<Long> studentIds) {
        Map<String, Object> comparison = new HashMap<>();
        List<Map<String, Object>> studentData = new ArrayList<>();

        for (Long studentId : studentIds) {
            User student = userRepository.findById(studentId).orElse(null);
            if (student == null) continue;

            Map<String, Object> data = new HashMap<>();
            data.put("id", studentId);
            data.put("name", student.getFullName());
            data.put("readinessScore", student.getPlacementReadinessScore());
            data.put("totalInterviews", student.getTotalInterviewsTaken());
            data.put("averageScore", student.getAverageScore());

            List<Interview> interviews = interviewRepository.findByStudentId(studentId);
            data.put("interviewHistory", interviews.stream()
                    .map(i -> Map.of(
                            "date", i.getCreatedAt().toString(),
                            "score", i.getOverallScore(),
                            "role", i.getJobRole()))
                    .collect(Collectors.toList()));

            studentData.add(data);
        }

        comparison.put("students", studentData);
        return comparison;
    }

    /**
     * Export college data
     */
    public Map<String, Object> exportCollegeData(Long collegeId, String format) {
        Map<String, Object> exportData = new HashMap<>();

        CollegeAnalyticsDTO analytics = getCollegeAnalytics(collegeId);
        List<User> students = userRepository.findByCollegeIdAndRole(
                collegeId, User.UserRole.ROLE_STUDENT);
        List<Interview> interviews = interviewRepository.findByCollegeId(
                collegeId, Pageable.unpaged()).getContent();

        exportData.put("analytics", analytics);
        exportData.put("students", students.stream()
                .map(this::mapToDetailedPerformanceDTO)
                .collect(Collectors.toList()));
        exportData.put("interviews", interviews.stream()
                .map(this::mapInterviewToDTO)
                .collect(Collectors.toList()));
        exportData.put("exportDate", LocalDateTime.now().toString());
        exportData.put("format", format);

        return exportData;
    }

    private Map<String, Object> mapInterviewToDTO(Interview interview) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", interview.getId());
        dto.put("studentId", interview.getStudent().getId());
        dto.put("studentName", interview.getStudent().getFullName());
        dto.put("jobRole", interview.getJobRole());
        dto.put("interviewType", interview.getInterviewType());
        dto.put("overallScore", interview.getOverallScore());
        dto.put("date", interview.getCreatedAt().toString());
        return dto;
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Long> computeRoleDistribution(Long collegeId) {
        var interviews = interviewRepository.findByCollegeId(collegeId, Pageable.unpaged());
        return interviews.stream()
                .map(Interview::getJobRole)
                .collect(Collectors.groupingBy(role -> role, Collectors.counting()));
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

    private StudentPerformanceDTO mapToDetailedPerformanceDTO(User student) {
        StudentPerformanceDTO dto = mapToBasicPerformanceDTO(student);

        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
        var recentInterviews = interviewRepository.findRecentInterviews(student.getId(), monthAgo);
        dto.setRecentInterviewsCount(recentInterviews.size());

        List<Double> scoreTrend = recentInterviews.stream()
                .sorted(Comparator.comparing(Interview::getCreatedAt))
                .map(Interview::getOverallScore)
                .collect(Collectors.toList());
        dto.setScoreTrend(scoreTrend);

        return dto;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}