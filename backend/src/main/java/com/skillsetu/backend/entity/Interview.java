package com.skillsetu.backend.entity;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(name = "interviews", indexes = {
        @Index(name = "idx_student", columnList = "student_id"),
        @Index(name = "idx_job_role", columnList = "job_role"),
        @Index(name = "idx_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Interview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "interview_type", nullable = false, length = 50)
    private String interviewType; // "Technical", "HR", "Aptitude", "Mixed"

    @Column(name = "job_role", nullable = false, length = 100)
    private String jobRole; // "Android Developer", etc.

    @Column(name = "overall_score", nullable = false)
    private Double overallScore; // 0.0 - 10.0

    @Column(name = "total_time", length = 20)
    private String totalTime; // e.g., "12:34"

    @Column(name = "is_retake")
    private Boolean isRetake = false;

    @Column(name = "ai_source", length = 50)
    private String aiSource; // "Groq" or "Gemini"

    // Store Q&A history as JSON
    @Column(name = "qa_history", columnDefinition = "TEXT")
    private String qaHistoryJson;

    // One-to-one relationship with evaluation
    @OneToOne(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    private Evaluation evaluation;

    // Link to generated roadmap
    @OneToOne(mappedBy = "interview", cascade = CascadeType.ALL)
    private Roadmap roadmap;
}