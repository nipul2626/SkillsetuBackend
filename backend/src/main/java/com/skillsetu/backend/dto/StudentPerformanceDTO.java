package com.skillsetu.backend.dto;
import lombok.Data;
import java.util.List;

@Data
public class StudentPerformanceDTO {
    private Long studentId;
    private String fullName;
    private String email;
    private Double placementReadinessScore;
    private Integer totalInterviews;
    private Double averageScore;
    private Integer recentInterviewsCount;
    private List<Double> scoreTrend;
}