package com.skillsetu.backend.entity;

import lombok.Data;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.*;

@Entity
@Immutable
@Table(name = "college_analytics_view")
@Data
public class CollegeAnalytics {

    @Id
    @Column(name = "college_id")
    private Long collegeId;

    @Column(name = "college_name")
    private String collegeName;

    @Column(name = "total_students")
    private Long totalStudents;

    @Column(name = "total_interviews")
    private Long totalInterviews;

    @Column(name = "average_score")
    private Double averageScore;

    @Column(name = "active_roadmaps")
    private Long activeRoadmaps;

    @Column(name = "avg_readiness_score")
    private Double avgReadinessScore;
}