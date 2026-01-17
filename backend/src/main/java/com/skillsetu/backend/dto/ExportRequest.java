package com.skillsetu.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 📄 Export Report Request
 */
@Data
public class ExportRequest {

    private String exportFormat;          // PDF, EXCEL, CSV
    private String reportType;            // STUDENT_LIST, ANALYTICS, SKILL_GAP

    // Date range
    private LocalDate startDate;
    private LocalDate endDate;

    // Filters (same as StudentFilterRequest)
    private List<String> branches;
    private List<Integer> years;
    private Double minReadinessScore;
    private Double maxReadinessScore;
    private List<String> jobRolePreferences;

    // Report customization
    private String reportTitle;
    private boolean includeCharts;
    private boolean includeDetailedAnalysis;
}