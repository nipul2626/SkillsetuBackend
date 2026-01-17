package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 📊 Skill Gap Analysis DTO
 */
@Data
public class SkillGapDTO {

    private List<SkillDeficiency> topDeficiencies;
    private Map<String, IndustryDemand> industryDemand;
    private List<TrainingRecommendation> recommendations;

    @Data
    public static class SkillDeficiency {
        private String skillName;
        private int studentsLacking;      // Number of students lacking this
        private double percentageLacking;  // % of total students
        private String severity;           // Critical, High, Medium, Low
    }

    @Data
    public static class IndustryDemand {
        private String skillName;
        private int demandCount;           // How many job roles need this
        private int supplyCount;           // How many students have this
        private double gapPercentage;      // Demand-supply gap
    }

    @Data
    public static class TrainingRecommendation {
        private String skillName;
        private int priority;              // 1-5 (1 = highest)
        private String reason;
        private int estimatedStudents;     // How many students need this
        private String suggestedDuration;  // "4 weeks", "8 weeks"
    }
}