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

    // ✅ FIX 1: Updated Gemini model name
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

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
     * ✅ FIXED: Main evaluation with better error handling
     */
    public CombinedResult evaluateInterview(List<QAPair> qaHistory) {
        log.info("Starting AI evaluation for {} Q&A pairs", qaHistory.size());

        // Try Groq first
        if (groqEnabled) {
            try {
                log.debug("Attempting evaluation with Groq...");
                CombinedResult result = callGroqEvaluation(qaHistory);
                if (result != null && isValidResult(result)) {
                    log.info("✅ Groq evaluation successful");
                    return result;
                }
            } catch (Exception e) {
                log.warn("⚠️ Groq evaluation failed: {}", e.getMessage());
            }
        }

        // Try Gemini fallback
        if (geminiEnabled) {
            try {
                log.debug("Attempting evaluation with Gemini...");
                CombinedResult result = callGeminiEvaluation(qaHistory);
                if (result != null && isValidResult(result)) {
                    log.info("✅ Gemini evaluation successful");
                    return result;
                }
            } catch (Exception e) {
                log.error("❌ Gemini evaluation failed: {}", e.getMessage());
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
            log.error("Groq question generation failed", e);
            throw new RuntimeException("Question generation failed");
        }
    }

    /**
     * ✅ FIXED: Better prompt that FORCES JSON output
     */
    private CombinedResult callGroqEvaluation(List<QAPair> qaHistory) {
        String prompt = buildStrictEvaluationPrompt(qaHistory);
        String response = callGroqAPI(prompt, 8192);
        return parseCombinedResult(response);
    }

    /**
     * ✅ FIXED: Groq API call with better error handling
     */
    private String callGroqAPI(String prompt, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "llama-3.1-8b-instant");
        requestBody.put("temperature", 0.3); // Lower temp for JSON
        requestBody.put("max_tokens", maxTokens);

        List<Map<String, String>> messages = new ArrayList<>();

        // ✅ System message to force JSON
        messages.add(Map.of(
                "role", "system",
                "content", "You are a JSON generator. Return ONLY valid JSON. No explanations, no markdown, no text before or after JSON."
        ));
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
                String content = (String) message.get("content");

                log.debug("Groq raw response length: {}", content.length());
                return content;
            }

            throw new RuntimeException("Empty response from Groq");

        } catch (Exception e) {
            log.error("Groq API call failed", e);
            throw new RuntimeException("Groq API error: " + e.getMessage(), e);
        }
    }

    // ==================== GEMINI IMPLEMENTATION ====================

    /**
     * ✅ FIXED: Gemini with updated model
     */
    private CombinedResult callGeminiEvaluation(List<QAPair> qaHistory) {
        String prompt = buildStrictEvaluationPrompt(qaHistory);
        String response = callGeminiAPI(prompt, 8192);
        return parseCombinedResult(response);
    }

    /**
     * ✅ FIXED: Gemini API with correct model name
     */
    private String callGeminiAPI(String prompt, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> part = Map.of("text", prompt);
        contents.add(Map.of("parts", List.of(part)));
        requestBody.put("contents", contents);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3); // Lower for JSON
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
                String text = parts.get(0).get("text");

                log.debug("Gemini raw response length: {}", text.length());
                return text;
            }

            throw new RuntimeException("Empty response from Gemini");

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        }
    }

    // ==================== IMPROVED PROMPT BUILDERS ====================

    private String buildQuestionGenerationPrompt(String jobRole, String interviewType) {
        return String.format(
                "Generate 10 interview questions for %s - %s.\n\n" +
                        "CRITICAL: Return ONLY this JSON structure, nothing else:\n" +
                        "{\n" +
                        "  \"questions\": [\n" +
                        "    {\n" +
                        "      \"type\": \"open_ended\",\n" +
                        "      \"question\": \"Your question here\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n\n" +
                        "No markdown, no explanations, ONLY JSON.",
                jobRole, interviewType
        );
    }

    /**
     * ✅ NEW: Stricter prompt that forces JSON output
     */
    private String buildStrictEvaluationPrompt(List<QAPair> qaHistory) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("CRITICAL INSTRUCTION: You MUST return ONLY valid JSON. No text before or after.\n\n");

        prompt.append("Evaluate this interview:\n\n");

        for (int i = 0; i < qaHistory.size(); i++) {
            QAPair qa = qaHistory.get(i);
            prompt.append(String.format("Q%d: %s\n", i + 1, truncate(qa.getQuestion(), 200)));
            prompt.append(String.format("A%d: %s\n\n", i + 1, truncate(qa.getAnswer(), 300)));
        }

        prompt.append("\nReturn EXACTLY this JSON structure (no extra text):\n\n");
        prompt.append("{\n");
        prompt.append("  \"evaluation\": {\n");
        prompt.append("    \"overallScore\": 7.5,\n");
        prompt.append("    \"scoreBreakdown\": {\n");
        prompt.append("      \"technicalKnowledge\": 7.0,\n");
        prompt.append("      \"problemSolving\": 7.5,\n");
        prompt.append("      \"communication\": 8.0,\n");
        prompt.append("      \"depthOfUnderstanding\": 7.0\n");
        prompt.append("    },\n");
        prompt.append("    \"coachFeedback\": \"Encouraging feedback in 2-3 sentences.\",\n");
        prompt.append("    \"questionAnalysis\": [\n");
        prompt.append("      {\n");
        prompt.append("        \"questionNumber\": 1,\n");
        prompt.append("        \"score\": 7.5,\n");
        prompt.append("        \"whatYouAnswered\": \"Brief summary\",\n");
        prompt.append("        \"whatWasGood\": \"Specific strengths\",\n");
        prompt.append("        \"whatWasMissing\": \"Specific gaps\",\n");
        prompt.append("        \"idealAnswer\": \"What perfect answer includes\"\n");
        prompt.append("      }\n");
        prompt.append("    ],\n");
        prompt.append("    \"topStrengths\": [\"Strength 1\", \"Strength 2\", \"Strength 3\"],\n");
        prompt.append("    \"criticalGaps\": [\"Gap 1\", \"Gap 2\"]\n");
        prompt.append("  },\n");
        prompt.append("  \"trainingPlan\": {\n");
        prompt.append("    \"readinessScore\": 65,\n");
        prompt.append("    \"targetScore\": 85,\n");
        prompt.append("    \"timeToTarget\": \"4 weeks with 2hrs/day\",\n");
        prompt.append("    \"focusAreas\": [\n");
        prompt.append("      {\n");
        prompt.append("        \"area\": \"Topic name\",\n");
        prompt.append("        \"priority\": \"High\",\n");
        prompt.append("        \"currentLevel\": 5,\n");
        prompt.append("        \"targetLevel\": 8,\n");
        prompt.append("        \"estimatedHours\": 20,\n");
        prompt.append("        \"keyTopics\": [\"Topic 1\", \"Topic 2\"],\n");
        prompt.append("        \"resources\": [\n");
        prompt.append("          {\n");
        prompt.append("            \"type\": \"Documentation\",\n");
        prompt.append("            \"title\": \"Resource name\",\n");
        prompt.append("            \"link\": \"https://example.com\",\n");
        prompt.append("            \"duration\": \"5 hours\"\n");
        prompt.append("          }\n");
        prompt.append("        ]\n");
        prompt.append("      }\n");
        prompt.append("    ],\n");
        prompt.append("    \"weeklyPlan\": [\n");
        prompt.append("      {\n");
        prompt.append("        \"week\": 1,\n");
        prompt.append("        \"theme\": \"Week theme\",\n");
        prompt.append("        \"studyTime\": \"90 min/day\",\n");
        prompt.append("        \"practiceTime\": \"30 min/day\",\n");
        prompt.append("        \"topics\": [\"Topic 1\", \"Topic 2\"],\n");
        prompt.append("        \"practiceProblems\": [\n");
        prompt.append("          {\n");
        prompt.append("            \"problem\": \"Problem description\",\n");
        prompt.append("            \"difficulty\": \"Easy\",\n");
        prompt.append("            \"focusArea\": \"What it practices\"\n");
        prompt.append("          }\n");
        prompt.append("        ],\n");
        prompt.append("        \"projects\": [\"Project description\"],\n");
        prompt.append("        \"weekendTask\": \"Weekend task\"\n");
        prompt.append("      }\n");
        prompt.append("    ],\n");
        prompt.append("    \"milestones\": [\n");
        prompt.append("      {\n");
        prompt.append("        \"week\": 1,\n");
        prompt.append("        \"milestone\": \"Achievement\",\n");
        prompt.append("        \"verification\": \"How to verify\"\n");
        prompt.append("      }\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("IMPORTANT:\n");
        prompt.append("- Include ALL 10 questionAnalysis items\n");
        prompt.append("- Include ALL 4 weeks in weeklyPlan\n");
        prompt.append("- Include 4 milestones\n");
        prompt.append("- Scores 5-8 are normal\n");
        prompt.append("- Be encouraging\n");
        prompt.append("- Return ONLY JSON, no markdown\n");

        return prompt.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    // ==================== IMPROVED PARSERS ====================

    private List<Question> parseQuestions(String response) {
        List<Question> questions = new ArrayList<>();
        try {
            String cleaned = cleanJsonResponse(response);

            log.debug("Parsing questions from: {}", cleaned.substring(0, Math.min(100, cleaned.length())));

            Map<String, Object> json = objectMapper.readValue(cleaned, Map.class);
            List<Map<String, Object>> questionsData = (List<Map<String, Object>>) json.get("questions");

            for (Map<String, Object> qData : questionsData) {
                Question q = new Question();
                q.setType((String) qData.get("type"));
                q.setText((String) qData.get("question"));

                if (qData.containsKey("options")) {
                    q.setOptions((List<String>) qData.get("options"));
                }

                if (qData.containsKey("correctIndex")) {
                    q.setCorrectIndex(((Number) qData.get("correctIndex")).intValue());
                } else {
                    q.setCorrectIndex(-1);
                }

                questions.add(q);
            }

            return questions;
        } catch (Exception e) {
            log.error("Failed to parse questions", e);
            throw new RuntimeException("Invalid question format");
        }
    }

    /**
     * ✅ IMPROVED: Better JSON cleaning and parsing
     */
    private CombinedResult parseCombinedResult(String response) {
        try {
            String cleaned = cleanJsonResponse(response);

            log.debug("Parsing evaluation from cleaned JSON (first 200 chars): {}",
                    cleaned.substring(0, Math.min(200, cleaned.length())));

            Map<String, Object> json = objectMapper.readValue(cleaned, Map.class);

            // Validate structure
            if (!json.containsKey("evaluation") || !json.containsKey("trainingPlan")) {
                log.error("Missing required keys in response");
                throw new RuntimeException("Invalid JSON structure");
            }

            Map<String, Object> evalMap = (Map<String, Object>) json.get("evaluation");
            Map<String, Object> planMap = (Map<String, Object>) json.get("trainingPlan");

            ComprehensiveEvaluation evaluation = parseEvaluation(evalMap);
            TrainingPlan trainingPlan = parseTrainingPlan(planMap);

            CombinedResult result = new CombinedResult();
            result.setEvaluation(evaluation);
            result.setTrainingPlan(trainingPlan);

            log.info("✅ Successfully parsed evaluation with score: {}", evaluation.getOverallScore());

            return result;

        } catch (Exception e) {
            log.error("Failed to parse evaluation result: {}", e.getMessage());
            log.error("Response was: {}", response.substring(0, Math.min(500, response.length())));
            throw new RuntimeException("Invalid evaluation format", e);
        }
    }

    /**
     * ✅ NEW: Aggressive JSON cleaning
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("Empty response");
        }

        String cleaned = response
                .replace("```json", "")
                .replace("```", "")
                .replace("**", "")
                .trim();

        // Find JSON object bounds
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        log.debug("Cleaned response length: {}", cleaned.length());

        return cleaned;
    }

    /**
     * Parse evaluation section
     */
    private ComprehensiveEvaluation parseEvaluation(Map<String, Object> evalMap) throws Exception {
        ComprehensiveEvaluation eval = new ComprehensiveEvaluation();

        eval.setOverallScore(((Number) evalMap.get("overallScore")).doubleValue());

        Map<String, Object> breakdown = (Map<String, Object>) evalMap.get("scoreBreakdown");
        Map<String, Double> scoreBreakdown = new HashMap<>();
        scoreBreakdown.put("technicalKnowledge", ((Number) breakdown.get("technicalKnowledge")).doubleValue());
        scoreBreakdown.put("problemSolving", ((Number) breakdown.get("problemSolving")).doubleValue());
        scoreBreakdown.put("communication", ((Number) breakdown.get("communication")).doubleValue());
        scoreBreakdown.put("depthOfUnderstanding", ((Number) breakdown.get("depthOfUnderstanding")).doubleValue());
        eval.setScoreBreakdown(scoreBreakdown);

        eval.setCoachFeedback((String) evalMap.get("coachFeedback"));

        List<Map<String, Object>> analysisData = (List<Map<String, Object>>) evalMap.get("questionAnalysis");
        List<QuestionAnalysis> questionAnalysis = new ArrayList<>();
        for (Map<String, Object> qa : analysisData) {
            QuestionAnalysis analysis = new QuestionAnalysis();
            analysis.setQuestionNumber(((Number) qa.get("questionNumber")).intValue());
            analysis.setScore(((Number) qa.get("score")).doubleValue());
            analysis.setWhatYouAnswered((String) qa.get("whatYouAnswered"));
            analysis.setWhatWasGood((String) qa.get("whatWasGood"));
            analysis.setWhatWasMissing((String) qa.get("whatWasMissing"));
            analysis.setIdealAnswer((String) qa.get("idealAnswer"));
            questionAnalysis.add(analysis);
        }
        eval.setQuestionAnalysis(questionAnalysis);

        eval.setTopStrengths((List<String>) evalMap.get("topStrengths"));
        eval.setCriticalGaps((List<String>) evalMap.get("criticalGaps"));

        return eval;
    }

    /**
     * Parse training plan section
     */
    private TrainingPlan parseTrainingPlan(Map<String, Object> planMap) throws Exception {
        TrainingPlan plan = new TrainingPlan();

        plan.setReadinessScore(((Number) planMap.get("readinessScore")).intValue());
        plan.setTargetScore(((Number) planMap.get("targetScore")).intValue());
        plan.setTimeToTarget((String) planMap.get("timeToTarget"));

        // Parse focus areas
        List<Map<String, Object>> focusData = (List<Map<String, Object>>) planMap.get("focusAreas");
        List<FocusArea> focusAreas = new ArrayList<>();
        for (Map<String, Object> fa : focusData) {
            FocusArea area = new FocusArea();
            area.setArea((String) fa.get("area"));
            area.setPriority((String) fa.get("priority"));
            area.setCurrentLevel(((Number) fa.get("currentLevel")).intValue());
            area.setTargetLevel(((Number) fa.get("targetLevel")).intValue());
            area.setEstimatedHours(((Number) fa.get("estimatedHours")).intValue());
            area.setKeyTopics((List<String>) fa.get("keyTopics"));

            List<Map<String, Object>> resourcesData = (List<Map<String, Object>>) fa.get("resources");
            List<Resource> resources = new ArrayList<>();
            for (Map<String, Object> r : resourcesData) {
                Resource resource = new Resource();
                resource.setType((String) r.get("type"));
                resource.setTitle((String) r.get("title"));
                resource.setLink((String) r.get("link"));
                resource.setDuration((String) r.get("duration"));
                resources.add(resource);
            }
            area.setResources(resources);
            focusAreas.add(area);
        }
        plan.setFocusAreas(focusAreas);

        // Parse weekly plan
        List<Map<String, Object>> weeksData = (List<Map<String, Object>>) planMap.get("weeklyPlan");
        List<WeeklyPlan> weeklyPlan = new ArrayList<>();
        for (Map<String, Object> w : weeksData) {
            WeeklyPlan week = new WeeklyPlan();
            week.setWeek(((Number) w.get("week")).intValue());
            week.setTheme((String) w.get("theme"));
            week.setStudyTime((String) w.get("studyTime"));
            week.setPracticeTime((String) w.get("practiceTime"));
            week.setTopics((List<String>) w.get("topics"));

            List<Map<String, Object>> problemsData = (List<Map<String, Object>>) w.get("practiceProblems");
            List<PracticeProblem> problems = new ArrayList<>();
            for (Map<String, Object> p : problemsData) {
                PracticeProblem problem = new PracticeProblem();
                problem.setProblem((String) p.get("problem"));
                problem.setDifficulty((String) p.get("difficulty"));
                problem.setFocusArea((String) p.get("focusArea"));
                problems.add(problem);
            }
            week.setPracticeProblems(problems);

            week.setProjects((List<String>) w.get("projects"));
            week.setWeekendTask((String) w.get("weekendTask"));
            weeklyPlan.add(week);
        }
        plan.setWeeklyPlan(weeklyPlan);

        // Parse milestones
        List<Map<String, Object>> milestonesData = (List<Map<String, Object>>) planMap.get("milestones");
        List<Milestone> milestones = new ArrayList<>();
        for (Map<String, Object> m : milestonesData) {
            Milestone milestone = new Milestone();
            milestone.setWeek(((Number) m.get("week")).intValue());
            milestone.setMilestone((String) m.get("milestone"));
            milestone.setVerification((String) m.get("verification"));
            milestones.add(milestone);
        }
        plan.setMilestones(milestones);

        return plan;
    }

    private boolean isValidResult(CombinedResult result) {
        return result != null
                && result.getEvaluation() != null
                && result.getEvaluation().getOverallScore() != null
                && result.getEvaluation().getQuestionAnalysis() != null
                && !result.getEvaluation().getQuestionAnalysis().isEmpty()
                && result.getTrainingPlan() != null
                && result.getTrainingPlan().getWeeklyPlan() != null
                && !result.getTrainingPlan().getWeeklyPlan().isEmpty();
    }

    public void shutdown() {
        // Cleanup if needed
    }

    // ==================== DATA CLASSES ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
        private ComprehensiveEvaluation evaluation;
        private TrainingPlan trainingPlan;
    }

    @Data
    public static class ComprehensiveEvaluation {
        private Double overallScore;
        private Map<String, Double> scoreBreakdown;
        private List<QuestionAnalysis> questionAnalysis;
        private String coachFeedback;
        private List<String> topStrengths;
        private List<String> criticalGaps;
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
    public static class FocusArea {
        private String area;
        private String priority;
        private Integer currentLevel;
        private Integer targetLevel;
        private Integer estimatedHours;
        private List<String> keyTopics;
        private List<Resource> resources;
    }

    @Data
    public static class Resource {
        private String type;
        private String title;
        private String link;
        private String duration;
    }

    @Data
    public static class WeeklyPlan {
        private Integer week;
        private String theme;
        private String studyTime;
        private String practiceTime;
        private List<String> topics;
        private List<PracticeProblem> practiceProblems;
        private List<String> projects;
        private String weekendTask;
    }

    @Data
    public static class PracticeProblem {
        private String problem;
        private String difficulty;
        private String focusArea;
    }

    @Data
    public static class Milestone {
        private Integer week;
        private String milestone;
        private String verification;
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
}