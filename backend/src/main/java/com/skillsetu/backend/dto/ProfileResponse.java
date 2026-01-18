package com.skillsetu.backend.dto;

import lombok.Data;

@Data
public class ProfileResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String college;
    private String branch;
    private String year;
    private Double cgpa;
    private Integer totalInterviews;
    private Integer skillsLearned;
    private Integer averageScore;
    private Double placementReadinessScore;
}