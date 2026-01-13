package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;
@Data
public class EvaluationDTO {
    private Double overallScore;
    private ScoreBreakdown scoreBreakdown;
    private List<QuestionAnalysis> questionAnalysis;
    private String coachFeedback;
    private List<String> topStrengths;
    private List<String> criticalGaps;

    @Data
    public static class ScoreBreakdown {
        private Double technicalKnowledge;
        private Double problemSolving;
        private Double communication;
        private Double depthOfUnderstanding;
    }

    @Data
    public static class QuestionAnalysis {
        private Integer questionNumber;
        private Double score;
        private String whatYouAnswered;
        private String whatWasGood;
        private String whatWasMissing;
        private String idealAnswer;
    }
}
