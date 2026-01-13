package com.skillsetu.backend.entity;

import lombok.*;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_college_role", columnList = "college_id, role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt hashed

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // STUDENT, TPO, ADMIN

    // Academic details (for students)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id")
    private College college;

    @Column(length = 100)
    private String branch; // e.g., "Computer Science"

    @Column(name = "year_of_study")
    private Integer yearOfStudy; // 1-4

    @Column(name = "enrollment_number", length = 50)
    private String enrollmentNumber;

    // Performance stats
    @Column(name = "placement_readiness_score")
    private Double placementReadinessScore = 0.0; // 0-100

    @Column(name = "total_interviews_taken")
    private Integer totalInterviewsTaken = 0;

    @Column(name = "average_score")
    private Double averageScore = 0.0;

    // Profile
    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Relationships
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Interview> interviews = new ArrayList<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Roadmap> roadmaps = new ArrayList<>();

    public enum UserRole {
        STUDENT, TPO, ADMIN
    }
}