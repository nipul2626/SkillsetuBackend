package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class InterviewRequest {
    private Long studentId;
    private String interviewType;
    private String jobRole;
    private String totalTime;
    private Boolean isRetake;
    private List<QAPair> qaHistory;

    @Data
    public static class QAPair {
        private String question;
        private String answer;
    }
}