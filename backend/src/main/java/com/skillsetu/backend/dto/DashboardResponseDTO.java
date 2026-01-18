package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class DashboardResponseDTO {

    private Long studentId;
    private String studentName;

    // Core metrics
    private int readinessScore;          // 0-100
    private int totalInterviews;
    private double averageScore;         // 0-10 scale
    private int weeklyTrend;             // Number of interviews this week
    private int roadmapCount;            // Number of generated roadmaps

    // Skills breakdown
    private List<SkillDTO> skills;

    // Recent interview activities
    private List<ActivityDTO> recentActivities;

    @Data
    public static class SkillDTO {
        private String name;
        private int percentage;  // 0-100
    }

    @Data
    public static class ActivityDTO {
        private String title;         // "Technical Interview - Software Engineer"
        private int score;            // 0-100
        private String timestamp;     // Formatted date
        private Long interviewId;     // For navigation
    }
}