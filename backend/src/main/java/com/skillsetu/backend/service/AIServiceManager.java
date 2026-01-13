// ==================== AI SERVICE MANAGER ====================
package com.skillsetu.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsetu.backend.util.CacheKeyBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Backend version of AIServiceManager
 * Handles all AI API calls with caching and fallback strategy
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIServiceManager {

    @Value("${ai.groq.api-key}")
    private String groqApiKey;

    @Value("${ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${ai.groq.enabled:true}")
    private boolean groqEnabled;

    @Value("${ai.gemini.enabled:true}")
    private boolean geminiEnabled;

    private final CacheService cacheService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";

    /**
     * Generate interview questions with caching
     */
    public List<Question> generateQuestions(String jobRole, String interviewType) {
        String cacheKey = CacheKeyBuilder.aiQuestionsKey(jobRole, interviewType);

        return cacheService.getOrCompute(
                cacheKey,
                List.class,
                () -> generateQuestionsFromAI(jobRole, interviewType),
                Duration.ofHours(24)
        );
    }

    /**
     * Async evaluation for heavy processing
     */
    @Async
    public CompletableFuture<CombinedResult> evaluateInterviewAsync(List<QAPair> qaHistory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return evaluateInterview(qaHistory);
            } catch (Exception e) {
                log.error("Async evaluation failed", e);
                throw new RuntimeException("Evaluation failed", e);
            }
        });
    }

    /**
     * Main evaluation method with fallback strategy
     */
    public CombinedResult evaluateInterview(List<QAPair> qaHistory) {
        log.info("Starting AI evaluation for {} Q&A pairs", qaHistory.size());

        // Try Groq first (faster)
        if (groqEnabled) {
            try {
                log.debug("Attempting evaluation with Groq...");
                CombinedResult result = callGroqEvaluation(qaHistory);
                if (result != null && isValidResult(result)) {
                    log.info("✅ Groq evaluation successful");
                    return result;
                }
            } catch (Exception e) {
                log.warn("⚠️ Groq evaluation failed, trying Gemini fallback", e);
            }
        }

        // Fallback to Gemini
        if (geminiEnabled) {
            try {
                log.debug("Attempting evaluation with Gemini...");
                CombinedResult result = callGeminiEvaluation(qaHistory);
                if (result != null && isValidResult(result)) {
                    log.info("✅ Gemini evaluation successful");
                    return result;
                }
            } catch (Exception e) {
                log.error("❌ Gemini evaluation also failed", e);
            }
        }

        throw new RuntimeException("All AI services failed");
    }

    // ==================== GROQ IMPLEMENTATION ====================

    private List<Question> generateQuestionsFromAI(String jobRole, String interviewType) {
        String prompt = buildQuestionGenerationPrompt(jobRole, interviewType);

        try {
            String response = callGroqAPI(prompt, 4096);
            return parseQuestions(response);
        } catch (Exception e) {
            log.error("Groq question generation failed, trying Gemini", e);
            String response = callGeminiAPI(prompt, 4096);
            return parseQuestions(response);
        }
    }

    private CombinedResult callGroqEvaluation(List<QAPair> qaHistory) {
        String prompt = buildEvaluationPrompt(qaHistory);
        String response = callGroqAPI(prompt, 8192);
        return parseCombinedResult(response);
    }

    private String callGroqAPI(String prompt, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "llama-3.1-8b-instant");
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", maxTokens);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + groqApiKey);
            headers.set("Content-Type", "application/json");

            var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
            var responseEntity = restTemplate.postForEntity(
                    GROQ_API_URL,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }

            throw new RuntimeException("Empty response from Groq");

        } catch (Exception e) {
            log.error("Groq API call failed", e);
            throw new RuntimeException("Groq API error", e);
        }
    }

    // ==================== GEMINI IMPLEMENTATION ====================

    private CombinedResult callGeminiEvaluation(List<QAPair> qaHistory) {
        String prompt = buildEvaluationPrompt(qaHistory);
        String response = callGeminiAPI(prompt, 8192);
        return parseCombinedResult(response);
    }

    private String callGeminiAPI(String prompt, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> part = Map.of("text", prompt);
        contents.add(Map.of("parts", List.of(part)));
        requestBody.put("contents", contents);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", maxTokens);
        requestBody.put("generationConfig", generationConfig);

        try {
            String url = GEMINI_API_URL + "?key=" + geminiApiKey;

            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/json");

            var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
            var responseEntity = restTemplate.postForEntity(url, entity, Map.class);

            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                return parts.get(0).get("text");
            }

            throw new RuntimeException("Empty response from Gemini");

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Gemini API error", e);
        }
    }

    // ==================== PROMPT BUILDERS ====================

    private String buildQuestionGenerationPrompt(String jobRole, String interviewType) {
        return String.format(
                "Generate 10 interview questions for a %s position (%s interview).\n\n" +
                        "Return ONLY valid JSON:\n" +
                        "{\n" +
                        "  \"questions\": [\n" +
                        "    {\n" +
                        "      \"type\": \"open_ended\",\n" +
                        "      \"question\": \"Your question here\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n\n" +
                        "Mix of question types: open_ended, mcq_proper, mcq_all_correct.",
                jobRole, interviewType
        );
    }

    private String buildEvaluationPrompt(List<QAPair> qaHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert technical interviewer. Evaluate this interview:\n\n");

        for (int i = 0; i < qaHistory.size(); i++) {
            QAPair qa = qaHistory.get(i);
            prompt.append(String.format("Q%d: %s\n", i + 1, qa.getQuestion()));
            prompt.append(String.format("A%d: %s\n\n", i + 1, qa.getAnswer()));
        }

        prompt.append("\nProvide evaluation in this JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"evaluation\": {\n");
        prompt.append("    \"overallScore\": 7.5,\n");
        prompt.append("    \"scoreBreakdown\": {...},\n");
        prompt.append("    \"coachFeedback\": \"...\",\n");
        prompt.append("    \"questionAnalysis\": [...]\n");
        prompt.append("  },\n");
        prompt.append("  \"trainingPlan\": {\n");
        prompt.append("    \"readinessScore\": 65,\n");
        prompt.append("    \"weeklyPlan\": [...]\n");
        prompt.append("  }\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    // ==================== PARSERS ====================

    private List<Question> parseQuestions(String response) {
        try {
            String cleaned = response.replace("```json", "").replace("```", "").trim();
            Map<String, Object> json = objectMapper.readValue(cleaned, Map.class);
            List<Map<String, Object>> questionsData = (List<Map<String, Object>>) json.get("questions");

            List<Question> questions = new ArrayList<>();
            for (Map<String, Object> qData : questionsData) {
                Question q = new Question();
                q.setType((String) qData.get("type"));
                q.setText((String) qData.get("question"));
                // Parse options if present
                questions.add(q);
            }

            return questions;
        } catch (Exception e) {
            log.error("Failed to parse questions", e);
            throw new RuntimeException("Invalid question format");
        }
    }

    private CombinedResult parseCombinedResult(String response) {
        try {
            String cleaned = response.replace("```json", "").replace("```", "").trim();
            return objectMapper.readValue(cleaned, CombinedResult.class);
        } catch (Exception e) {
            log.error("Failed to parse evaluation result", e);
            throw new RuntimeException("Invalid evaluation format");
        }
    }

    private boolean isValidResult(CombinedResult result) {
        return result != null
                && result.getEvaluation() != null
                && result.getEvaluation().getOverallScore() != null
                && result.getTrainingPlan() != null;
    }

    // ==================== DATA CLASSES ====================

    // Inside AIServiceManager.java (at the bottom)
    @Data
    @NoArgsConstructor  // Added for JSON parsing
    @AllArgsConstructor // Added for manual mapping
    public static class QAPair {
        private String question;
        private String answer;
    }

    @Data
    public static class Question {
        private String type;
        private String text;
        private List<String> options;
        private Integer correctIndex;
    }

    @Data
    public static class CombinedResult {
        private EvaluationResult evaluation;
        private TrainingPlan trainingPlan;
    }

    @Data
    public static class EvaluationResult {
        private Double overallScore;
        private Map<String, Double> scoreBreakdown;
        private String coachFeedback;
        private List<String> topStrengths;
        private List<String> criticalGaps;
        private List<QuestionAnalysis> questionAnalysis;
        private List<ImmediateAction> immediateActions;
    }

    @Data
    public static class QuestionAnalysis {
        private Integer questionNumber;
        private Double score;
        private String whatYouAnswered;
        private String whatWasGood;
        private String whatWasMissing;
        private String idealAnswer;
    }

    @Data
    public static class ImmediateAction {
        private String priority;
        private String action;
        private String why;
        private List<String> resources;
    }

    @Data
    public static class TrainingPlan {
        private Integer readinessScore;
        private Integer targetScore;
        private String timeToTarget;
        private List<FocusArea> focusAreas;
        private List<WeeklyPlan> weeklyPlan;
        private List<Milestone> milestones;
    }

    @Data
    public static class FocusArea {
        private String area;
        private String priority;
        private Integer currentLevel;
        private Integer targetLevel;
    }

    @Data
    public static class WeeklyPlan {
        private Integer week;
        private String theme;
        private List<String> topics;
        private List<PracticeProblem> practiceProblems;
    }

    @Data
    public static class PracticeProblem {
        private String problem;
        private String difficulty;
    }

    @Data
    public static class Milestone {
        private Integer week;
        private String milestone;
        private String verification;
    }
}