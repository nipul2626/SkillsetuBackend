package com.skillsetu.backend.service;

import com.skillsetu.backend.dto.*;
import com.skillsetu.backend.entity.Interview;
import com.skillsetu.backend.entity.User;
import com.skillsetu.backend.repository.*;
import com.skillsetu.backend.util.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
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

    // =========================================================
    // COLLEGE ANALYTICS (CACHED)
    // =========================================================

    @Cacheable(value = "college_analytics", key = "#collegeId")
    public CollegeAnalyticsDTO getCollegeAnalytics(Long collegeId) {

        log.info("📊 Computing analytics for college {}", collegeId);

        return computeCollegeAnalytics(collegeId);
    }

    private CollegeAnalyticsDTO computeCollegeAnalytics(Long collegeId) {

        CollegeAnalyticsDTO dto = new CollegeAnalyticsDTO();

        List<User> students =
                userRepository.findByCollegeIdAndRole(
                        collegeId, User.UserRole.ROLE_STUDENT);

        dto.setTotalStudents((long) students.size());

        dto.setTotalInterviews(
                interviewRepository.countByCollegeId(collegeId));

        dto.setActiveRoadmaps(
                roadmapRepository.countByStudent_College_Id(collegeId)
        );



        dto.setAverageInterviewScore(
                Optional.ofNullable(
                                interviewRepository.getAverageScoreByCollege(collegeId))
                        .orElse(0.0));

        dto.setAverageReadinessScore(
                Optional.ofNullable(
                                userRepository.getAverageReadinessScoreByCollege(collegeId))
                        .orElse(0.0));

        dto.setTopPerformers(
                students.stream()
                        .sorted(Comparator.comparing(
                                User::getPlacementReadinessScore).reversed())
                        .limit(10)
                        .map(this::mapToBasicPerformanceDTO)
                        .toList());

        dto.setJobRoleDistribution(computeRoleDistribution(collegeId));

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        dto.setRecentInterviewsCount(
                interviewRepository.findByCollegeId(collegeId, Pageable.unpaged())
                        .stream()
                        .filter(i -> i.getCreatedAt().isAfter(weekAgo))
                        .count());

        return dto;
    }

    // =========================================================
    // DASHBOARD STATS
    // =========================================================

    public Map<String, Object> getDashboardStats(Long collegeId) {

        CollegeAnalyticsDTO analytics = getCollegeAnalytics(collegeId);
        Map<String, Object> map = new HashMap<>();

        map.put("totalStudents", analytics.getTotalStudents());
        map.put("totalInterviews", analytics.getTotalInterviews());
        map.put("activeRoadmaps", analytics.getActiveRoadmaps());
        map.put("avgInterviewScore", analytics.getAverageInterviewScore());
        map.put("avgReadinessScore", analytics.getAverageReadinessScore());

        return map;
    }

    // =========================================================
    // TPO STUDENT LIST
    // =========================================================

    public Map<String, Object> getFilteredStudents(
            Long collegeId,
            StudentFilterRequest filterRequest) {

        List<StudentPerformanceDTO> students =
                userRepository.findByCollegeIdAndRole(
                                collegeId, User.UserRole.ROLE_STUDENT)
                        .stream()
                        .map(this::mapToStudentDTO)
                        .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("students", students);
        response.put("total", students.size());

        return response;
    }

    // =========================================================
    // CACHE CLEAR (REFRESH BUTTON SUPPORT)
    // =========================================================

    @CacheEvict(value = "college_analytics", key = "#collegeId")
    public void clearCollegeAnalyticsCache(Long collegeId) {
        log.info("🧹 Cleared analytics cache for college {}", collegeId);
    }

    // =========================================================
    // DTO MAPPERS
    // =========================================================

    private StudentPerformanceDTO mapToStudentDTO(User student) {

        StudentPerformanceDTO dto = new StudentPerformanceDTO();

        dto.setStudentId(student.getId());
        dto.setFullName(student.getFullName());
        dto.setEmail(student.getEmail());

        dto.setPlacementReadinessScore(
                Optional.ofNullable(student.getPlacementReadinessScore())
                        .orElse(0.0));

        dto.setTotalInterviews(
                Optional.ofNullable(student.getTotalInterviewsTaken())
                        .orElse(0));

        dto.setAverageScore(
                Optional.ofNullable(student.getAverageScore())
                        .orElse(0.0));

        return dto;
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

    // =========================================================
    // UTIL
    // =========================================================

    private Map<String, Long> computeRoleDistribution(Long collegeId) {

        return interviewRepository
                .findByCollegeId(collegeId, Pageable.unpaged())
                .stream()
                .map(Interview::getJobRole)
                .collect(Collectors.groupingBy(
                        role -> role, Collectors.counting()));
    }

// ======================= REQUIRED METHODS =======================

    public List<StudentPerformanceDTO> getStudentsNeedingAttention(Long collegeId, int limit) {
        return userRepository.findByCollegeIdAndRole(collegeId, User.UserRole.ROLE_STUDENT)
                .stream()
                .filter(s -> s.getPlacementReadinessScore() != null
                        && s.getPlacementReadinessScore() < 60)
                .sorted(Comparator.comparing(User::getPlacementReadinessScore))
                .limit(limit)
                .map(this::mapToBasicPerformanceDTO)
                .toList();
    }

    public StudentPerformanceDTO getStudentPerformance(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        StudentPerformanceDTO dto = mapToBasicPerformanceDTO(student);

        List<Interview> interviews =
                interviewRepository.findByStudentId(studentId);

        dto.setRecentInterviewsCount(interviews.size());

        dto.setScoreTrend(
                interviews.stream()
                        .sorted(Comparator.comparing(Interview::getCreatedAt))
                        .map(Interview::getOverallScore)
                        .toList()
        );

        return dto;
    }

    public SkillGapDTO getSkillGaps(Long collegeId, String jobRole, int limit) {
        SkillGapDTO dto = new SkillGapDTO();

        List<User> students =
                userRepository.findByCollegeIdAndRole(collegeId, User.UserRole.ROLE_STUDENT);

        Map<String, Integer> gaps = new HashMap<>();

        for (User student : students) {
            List<Interview> interviews =
                    interviewRepository.findByStudentId(student.getId());

            for (Interview i : interviews) {
                if (i.getOverallScore() < 6) {
                    gaps.merge(i.getJobRole(), 1, Integer::sum);
                }
            }
        }

        List<SkillGapDTO.SkillDeficiency> deficiencies =
                gaps.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(limit)
                        .map(e -> {
                            SkillGapDTO.SkillDeficiency d =
                                    new SkillGapDTO.SkillDeficiency();
                            d.setSkillName(e.getKey());
                            d.setStudentsLacking(e.getValue());
                            d.setSeverity(
                                    e.getValue() > 20 ? "Critical" :
                                            e.getValue() > 10 ? "High" : "Medium");
                            return d;
                        })
                        .toList();

        dto.setTopDeficiencies(deficiencies);
        return dto;
    }

    public TrendDataDTO getTrends(Long collegeId, int days) {
        TrendDataDTO dto = new TrendDataDTO();

        LocalDateTime start = LocalDateTime.now().minusDays(days);

        List<Interview> interviews =
                interviewRepository.findByCollegeId(collegeId, Pageable.unpaged())
                        .getContent()
                        .stream()
                        .filter(i -> i.getCreatedAt().isAfter(start))
                        .toList();

        Map<String, List<Interview>> byMonth =
                interviews.stream()
                        .collect(Collectors.groupingBy(
                                i -> i.getCreatedAt().getMonth() + " " + i.getCreatedAt().getYear()
                        ));

        List<TrendDataDTO.MonthlyTrend> monthly =
                byMonth.entrySet().stream()
                        .map(e -> {
                            TrendDataDTO.MonthlyTrend mt =
                                    new TrendDataDTO.MonthlyTrend();
                            mt.setMonth(e.getKey());
                            mt.setTotalInterviews(e.getValue().size());
                            mt.setAverageScore(
                                    e.getValue().stream()
                                            .mapToDouble(Interview::getOverallScore)
                                            .average()
                                            .orElse(0)
                            );
                            return mt;
                        })
                        .toList();

        dto.setMonthlyTrends(monthly);
        return dto;
    }

    public Map<String, Object> getBranchWiseStats(Long collegeId) {
        Map<String, Object> result = new HashMap<>();

        List<User> students =
                userRepository.findByCollegeIdAndRole(collegeId, User.UserRole.ROLE_STUDENT);

        Map<String, List<User>> grouped =
                students.stream()
                        .filter(s -> s.getBranch() != null)
                        .collect(Collectors.groupingBy(User::getBranch));

        Map<String, Map<String, Object>> branchStats = new HashMap<>();

        for (var entry : grouped.entrySet()) {
            Map<String, Object> data = new HashMap<>();
            data.put("totalStudents", entry.getValue().size());
            data.put("avgReadiness",
                    entry.getValue().stream()
                            .mapToDouble(User::getPlacementReadinessScore)
                            .average()
                            .orElse(0));
            branchStats.put(entry.getKey(), data);
        }

        result.put("branches", branchStats);
        return result;
    }

}
