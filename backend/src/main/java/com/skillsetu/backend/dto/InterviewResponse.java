package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class InterviewResponse {
    private Long interviewId;
    private Double overallScore;
    private EvaluationDTO evaluation;
    private RoadmapDTO roadmap;
    private String message;
}
