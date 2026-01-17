package com.skillsetu.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 📈 Placement Trends Data
 */
@Data
public class TrendDataDTO {

    private List<MonthlyTrend> monthlyTrends;
    private ComparisonData batchComparison;

    @Data
    public static class MonthlyTrend {
        private String month;              // "Jan 2025"
        private int totalInterviews;
        private double averageScore;
        private int studentsImproved;      // Students who improved this month
        private double avgReadinessScore;
    }

    @Data
    public static class ComparisonData {
        private String currentBatch;       // "2024"
        private String previousBatch;      // "2023"
        private double currentAvgScore;
        private double previousAvgScore;
        private double improvement;        // Percentage improvement
    }
}