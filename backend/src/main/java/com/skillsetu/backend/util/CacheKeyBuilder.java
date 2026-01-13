package com.skillsetu.backend.util;

public class CacheKeyBuilder {

    private static final String SEPARATOR = ":";

    // Question cache keys
    public static String aiQuestionsKey(String jobRole, String interviewType) {
        return String.format("ai_questions%s%s%s%s",
                SEPARATOR, jobRole, SEPARATOR, interviewType);
    }

    // Student readiness score
    public static String readinessScoreKey(Long studentId) {
        return String.format("readiness_score%s%d", SEPARATOR, studentId);
    }

    // Active roadmap
    public static String activeRoadmapKey(Long studentId) {
        return String.format("active_roadmap%s%d", SEPARATOR, studentId);
    }

    // College analytics
    public static String collegeAnalyticsKey(Long collegeId) {
        return String.format("college_analytics%s%d", SEPARATOR, collegeId);
    }

    // Student pattern (for invalidation)
    public static String studentPattern(Long studentId) {
        return String.format("*%s%d%s*", SEPARATOR, studentId, SEPARATOR);
    }

    // JWT session
    public static String userSessionKey(String userId) {
        return String.format("user_session%s%s", SEPARATOR, userId);
    }
}