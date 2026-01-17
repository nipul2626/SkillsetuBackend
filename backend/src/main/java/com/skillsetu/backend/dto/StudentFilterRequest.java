package com.skillsetu.backend.dto;

import lombok.Data;
import java.util.List;

/**
 * 🎯 TPO Student Filter Request
 * Used for filtering students in TPO dashboard
 */
@Data
public class StudentFilterRequest {

    // Basic filters
    private String searchQuery;           // Search by name or email
    private List<String> branches;        // CS, IT, ECE, etc.
    private List<Integer> years;          // 1, 2, 3, 4

    // Readiness score ranges
    private Double minReadinessScore;     // 0-100
    private Double maxReadinessScore;     // 0-100

    // Additional filters
    private List<String> jobRolePreferences;  // Android Developer, etc.
    private List<String> skillTags;           // Java, Python, etc.

    // Sorting
    private String sortBy;                // name, readiness, lastInterview
    private String sortOrder;             // asc, desc

    // Pagination
    private int page = 0;
    private int size = 20;

    // Date range for "needs attention"
    private Integer daysInactive;         // Students inactive for X days
}