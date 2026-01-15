package com.skillsetu.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "roadmaps", indexes = {
        @Index(name = "idx_student", columnList = "student_id"),
        @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Roadmap extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private User student;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id")
    private Interview interview; // The interview that generated this roadmap

    @Column(name = "job_role", nullable = false, length = 100)
    private String jobRole;

    @Column(name = "interview_type", nullable = false, length = 50)
    private String interviewType;

    @Column(name = "readiness_score", nullable = false)
    private Integer readinessScore; // 0-100

    @Column(name = "target_score", nullable = false)
    private Integer targetScore; // 0-100

    @Column(name = "time_to_target", length = 100)
    private String timeToTarget; // e.g., "6 weeks with 3hrs/day"

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_completion_date")
    private LocalDate targetCompletionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoadmapStatus status = RoadmapStatus.ACTIVE;

    // Progress tracking
    @Column(name = "completed_tasks_count")
    private Integer completedTasksCount = 0;

    @Column(name = "total_tasks_count")
    private Integer totalTasksCount = 0;

    @Column(name = "progress_percentage")
    private Double progressPercentage = 0.0;

    // Store completed task IDs as comma-separated string or JSON
    @Column(name = "completed_task_ids", columnDefinition = "TEXT")
    private String completedTaskIdsJson;

    // Training plan details stored as JSON
    @Column(name = "focus_areas", columnDefinition = "TEXT")
    private String focusAreasJson;

    @Column(name = "weekly_plan", columnDefinition = "TEXT")
    private String weeklyPlanJson;

    @Column(name = "milestones", columnDefinition = "TEXT")
    private String milestonesJson;

    public enum RoadmapStatus {
        ACTIVE, COMPLETED, ABANDONED, PAUSED
    }

    // Helper method to calculate progress
    public void updateProgress() {
        if (totalTasksCount > 0) {
            this.progressPercentage = (completedTasksCount * 100.0) / totalTasksCount;
        }
    }
}
