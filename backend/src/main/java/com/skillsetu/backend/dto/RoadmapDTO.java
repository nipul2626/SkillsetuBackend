package com.skillsetu.backend.dto;
import lombok.Data;
import java.util.List;

@Data
public class RoadmapDTO {
    private Long roadmapId;
    private Integer readinessScore;
    private Integer targetScore;
    private String timeToTarget;
    private List<FocusArea> focusAreas;
    private List<WeeklyPlan> weeklyPlan;

    @Data
    public static class FocusArea {
        private String area;
        private String priority;
        private Integer currentLevel;
        private Integer targetLevel;
        private Integer estimatedHours;
    }

    @Data
    public static class WeeklyPlan {
        private Integer week;
        private String theme;
        private String studyTime;
        private List<String> topics;
    }
}