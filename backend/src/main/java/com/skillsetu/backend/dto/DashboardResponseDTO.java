package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class DashboardResponseDTO {

    private Long studentId;
    private String studentName;

    private Integer readinessScore;
    private Integer totalInterviews;
    private Double averageScore;
    private Integer weeklyTrend;

    private List<SkillDTO> skills;
    private List<ActivityDTO> recentActivities;

    @Data
    public static class SkillDTO {
        private String name;
        private Integer percentage;
    }

    @Data
    public static class ActivityDTO {
        private String title;
        private Integer score;
        private String timestamp;
    }
}
