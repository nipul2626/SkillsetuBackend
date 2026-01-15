package com.skillsetu.backend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 🛡️ Answer Quality Validator
 *
 * Detects low-quality answers BEFORE sending to AI
 * - Clipboard text
 * - Generic phrases
 * - Too short answers
 * - Question echo
 */
@Component
@Slf4j
public class AnswerValidator {

    // 🚫 Clipboard/System text patterns
    private static final Pattern[] GARBAGE_PATTERNS = {
            Pattern.compile("(?i)clipboard"),
            Pattern.compile("(?i)gboard"),
            Pattern.compile("(?i)pin.*clip"),
            Pattern.compile("(?i)touch.*hold"),
            Pattern.compile("(?i)edit icon"),
            Pattern.compile("(?i)copy.*save.*here"),
            Pattern.compile("(?i)tap.*paste"),
            Pattern.compile("(?i)welcome to"),
            Pattern.compile("(?i)deleted after.*hour"),
            Pattern.compile("(?i)use the.*icon")
    };

    // 🚫 Generic placeholder phrases
    private static final Pattern[] GENERIC_PATTERNS = {
            Pattern.compile("(?i)^(yes|no|maybe|ok|okay)$"),
            Pattern.compile("(?i)^i (don't|dont) know$"),
            Pattern.compile("(?i)^(correct|wrong|right)$"),
            Pattern.compile("(?i)^selected:.*$") // Just MCQ selection without explanation
    };

    /**
     * 🔍 Validate answer quality
     *
     * @return ValidationResult with quality flags
     */
    public ValidationResult validate(String question, String answer) {
        ValidationResult result = new ValidationResult();

        if (answer == null || answer.trim().isEmpty()) {
            result.setValid(false);
            result.setReason("Empty answer");
            result.setSuggestedScoreCap(0.0);
            return result;
        }

        String cleaned = answer.trim();
        String[] words = cleaned.split("\\s+");
        int wordCount = words.length;

        // ❌ CHECK 1: Too short (minimum 20 words for open-ended)
        if (!answer.startsWith("Selected:") && wordCount < 20) {
            result.setValid(false);
            result.setReason("Answer too short (< 20 words)");
            result.setSuggestedScoreCap(2.0);
            return result;
        }

        // ❌ CHECK 2: Clipboard/System text
        for (Pattern pattern : GARBAGE_PATTERNS) {
            if (pattern.matcher(cleaned).find()) {
                result.setValid(false);
                result.setReason("Clipboard/system text detected");
                result.setSuggestedScoreCap(0.0);
                log.warn("🚫 Garbage text detected: {}", cleaned.substring(0, Math.min(50, cleaned.length())));
                return result;
            }
        }

        // ❌ CHECK 3: Generic placeholder
        for (Pattern pattern : GENERIC_PATTERNS) {
            if (pattern.matcher(cleaned).matches()) {
                result.setValid(false);
                result.setReason("Generic/placeholder answer");
                result.setSuggestedScoreCap(1.0);
                return result;
            }
        }

        // ❌ CHECK 4: Question echo (answer repeats question)
        double similarity = calculateSimilarity(question.toLowerCase(), cleaned.toLowerCase());
        if (similarity > 0.7) {
            result.setValid(false);
            result.setReason("Answer is copy of question");
            result.setSuggestedScoreCap(0.0);
            return result;
        }

        // ❌ CHECK 5: MCQ without explanation
        if (answer.startsWith("Selected:") && answer.split("\n").length < 2) {
            result.setValid(false);
            result.setReason("MCQ selection without explanation");
            result.setSuggestedScoreCap(2.0);
            return result;
        }

        // ✅ PASSED ALL CHECKS
        result.setValid(true);
        result.setReason("Quality answer");
        result.setSuggestedScoreCap(10.0);

        return result;
    }

    /**
     * 📊 Calculate text similarity (Jaccard Index)
     */
    private double calculateSimilarity(String text1, String text2) {
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");

        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));

        // Intersection
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // Union
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0.0;

        return (double) intersection.size() / union.size();
    }

    /**
     * 📦 Validation Result
     */
    public static class ValidationResult {
        private boolean valid;
        private String reason;
        private double suggestedScoreCap;

        public ValidationResult() {}

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public double getSuggestedScoreCap() { return suggestedScoreCap; }
        public void setSuggestedScoreCap(double cap) { this.suggestedScoreCap = cap; }
    }
}