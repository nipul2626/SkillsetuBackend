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
    private List<Milestone> milestones;

    @Data
    public static class FocusArea {
        private String area;
        private String priority;
        private Integer currentLevel;
        private Integer targetLevel;
        private Integer estimatedHours;
        private List<String> keyTopics;        // ✅ ADD
        private List<Resource> resources;       // ✅ ADD
    }

    // ✅ NEW: Resource class
    @Data
    public static class Resource {
        private String type;
        private String title;
        private String link;
        private String duration;
    }

    @Data
    public static class WeeklyPlan {
        private Integer week;
        private String theme;
        private String studyTime;
        private String practiceTime;           // ✅ ADD
        private List<String> topics;
        private List<PracticeProblem> practiceProblems;  // ✅ ADD
        private List<String> projects;         // ✅ ADD
        private String weekendTask;            // ✅ ADD
    }

    // ✅ NEW: PracticeProblem class
    @Data
    public static class PracticeProblem {
        private String problem;
        private String difficulty;
        private String focusArea;
    }
    @Data
    public static class Milestone {
        private Integer week;
        private String milestone;
        private String verification;
    }

}