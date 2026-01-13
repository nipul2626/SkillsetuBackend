package com.skillsetu.backend.dto;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class CollegeAnalyticsDTO {
    private Long totalStudents;
    private Long totalInterviews;
    private Long activeRoadmaps;
    private Double averageInterviewScore;
    private Double averageReadinessScore;
    private Long recentInterviewsCount;
    private List<StudentPerformanceDTO> topPerformers;
    private Map<String, Long> jobRoleDistribution;
}