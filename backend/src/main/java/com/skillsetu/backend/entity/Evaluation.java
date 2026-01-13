package com.skillsetu.backend.entity;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(name = "evaluations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Evaluation extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    // Score breakdown
    @Column(name = "technical_knowledge_score")
    private Double technicalKnowledgeScore;

    @Column(name = "problem_solving_score")
    private Double problemSolvingScore;

    @Column(name = "communication_score")
    private Double communicationScore;

    @Column(name = "depth_of_understanding_score")
    private Double depthOfUnderstandingScore;

    // Detailed feedback
    @Column(name = "coach_feedback", columnDefinition = "TEXT")
    private String coachFeedback;

    @Column(name = "top_strengths", columnDefinition = "TEXT")
    private String topStrengthsJson; // JSON array

    @Column(name = "critical_gaps", columnDefinition = "TEXT")
    private String criticalGapsJson; // JSON array

    // Question analysis stored as JSON
    @Column(name = "question_analysis", columnDefinition = "TEXT")
    private String questionAnalysisJson;

    // Immediate actions
    @Column(name = "immediate_actions", columnDefinition = "TEXT")
    private String immediateActionsJson;
}