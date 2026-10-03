package com.notepilot.notepilot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String HF_URL = "https://router.huggingface.co/v1/chat/completions";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String[] LETTERS = {"A", "B", "C", "D"};
    private static final int QUIZ_COUNT = 5;
    private static final int FLASHCARD_COUNT = 8;

    @Value("${HF_TOKEN}")
    private String hfToken;

    @Value("${notepilot.ai.model:deepseek-ai/DeepSeek-V4.1-Flash}")
    private String model;

    @Value("${notepilot.ai.max-tokens:8000}")
    private int maxTokens;

    @Value("${notepilot.ai.reasoning-effort:}")
    private String reasoningEffort;

    private final RestTemplate restTemplate;

    public AiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(180)); // reasoning models can be slow
        this.restTemplate = new RestTemplate(factory);
    }

    /** Thrown for any AI/API failure. The message is safe to show to the user (no secrets). */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) {
            super(message);
        }

        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ------------------------------------------------------------------ public API

    public String generateSummary(String notes) {
        String system = """
                You are an expert study assistant. Create an exam-ready revision summary based STRICTLY on the \
                notes supplied by the user. Do not invent facts or add unrelated material, and preserve the \
                original meaning.

                Format the answer in simple Markdown:
                - Start with a meaningful title using '# '.
                - Use '## ' headings and '### ' subheadings where appropriate.
                - Use concise explanations and '- ' bullet points.
                - Put important definitions and keywords in **bold**.
                - End with a '## Key Takeaways' section.
                Do not include your reasoning, preamble or closing remarks.
                """;
        return callModel(system, notes);
    }

    public String generateQuiz(String notes) {
        String system = """
                You are an expert exam writer. Create exactly 5 distinct, useful multiple-choice questions based \
                only on the user's notes. Each question must have one clear question, four options labelled A, B, \
                C and D, exactly one correct answer given as the single letter A, B, C or D, and a short explanation.

                Return ONLY valid JSON in exactly this schema, with no Markdown fences, introduction or commentary:
                {
                  "questions": [
                    {
                      "question": "Question text",
                      "options": {"A": "First option", "B": "Second option", "C": "Third option", "D": "Fourth option"},
                      "answer": "A",
                      "explanation": "Explanation of the correct answer"
                    }
                  ]
                }
                """;
        String content = callModel(system, notes);
        return parseQuiz(content);
    }

    public String generateFlashcards(String notes) {
        String system = """
                You are an expert study coach. Create exactly 8 distinct flashcards based only on the user's \
                notes. Each flashcard has one concise question and one accurate answer. Do not duplicate questions.

                Return ONLY valid JSON in exactly this schema, with no Markdown fences, introduction or commentary:
                {
                  "flashcards": [
                    {"question": "Question text", "answer": "Answer text"}
                  ]
                }
                """;
        String content = callModel(system, notes);
        return parseFlashcards(content);
    }

    // ------------------------------------------------------------------ HTTP call

    private String callModel(String systemPrompt, String notes) {
        if (hfToken == null || hfToken.isBlank()) {
            throw new AiServiceException("The server has no Hugging Face token configured (HF_TOKEN).");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", notes)));
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort.trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hfToken.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    HF_URL, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return extractContent(response.getBody());
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.error("Hugging Face API returned HTTP {}: {}", status, truncate(e.getResponseBodyAsString(), 500));
            throw new AiServiceException(describeStatus(status), e);
        } catch (ResourceAccessException e) {
            log.error("Could not reach Hugging Face (network error or timeout): {}", e.getMessage());
            throw new AiServiceException(
                    "Could not reach the Hugging Face API (network error or timeout). Please try again.", e);
        } catch (RestClientException e) {
            log.error("Hugging Face call failed: {}", e.getMessage());
            throw new AiServiceException("The Hugging Face API call failed. Check the backend logs.", e);
        }
    }

    private static String describeStatus(int status) {
        return switch (status) {
            case 401, 403 -> "Hugging Face rejected the token (HTTP " + status
                    + "). Check HF_TOKEN and that it may call Inference Providers.";
            case 404 -> "Hugging Face could not find the model or endpoint (HTTP 404).";
            case 429 -> "Hugging Face rate limit or quota reached (HTTP 429). Wait a bit and try again.";
            default -> "Hugging Face returned an error (HTTP " + status + ").";
        };
    }

    // ------------------------------------------------------------------ response extraction

    /**
     * Pulls choices[0].message.content out of an OpenAI-compatible response.
     * Never treats reasoning_content as the answer and never returns an empty string.
     */
    static String extractContent(Map<String, Object> response) {
        if (response == null) {
            throw new AiServiceException("Hugging Face returned an empty response.");
        }
        if (!(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            log.error("AI response has no 'choices'. Top-level keys: {}", response.keySet());
            throw new AiServiceException("Hugging Face returned a response without any choices.");
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            log.error("AI response 'choices[0]' is not an object.");
            throw new AiServiceException("Hugging Face returned a malformed response (choices[0]).");
        }
        Object finishReason = choice.get("finish_reason");
        if (!(choice.get("message") instanceof Map<?, ?> message)) {
            log.error("AI response has no 'message' object. finish_reason={}", finishReason);
            throw new AiServiceException("Hugging Face returned a response without a message.");
        }

        Object content = message.get("content");
        Object reasoning = message.get("reasoning_content");
        int reasoningLength = reasoning instanceof String r ? r.length() : 0;
        log.info("AI response: finish_reason={}, contentLength={}, reasoningLength={}, usage={}",
                finishReason,
                content instanceof String c ? c.length() : 0,
                reasoningLength,
                response.get("usage"));

        if (content instanceof String text && !text.isBlank()) {
            if ("length".equals(finishReason)) {
                log.warn("Model output was cut off (finish_reason=length); it may be incomplete.");
            }
            return text;
        }

        log.error("Model returned no final content. finish_reason={}, reasoning_content present={}",
                finishReason, reasoningLength > 0);
        if ("length".equals(finishReason)) {
            throw new AiServiceException("The model used all of its output tokens before writing an answer "
                    + "(finish_reason=length). It is a reasoning model, and its thinking counts against max_tokens. "
                    + "Increase notepilot.ai.max-tokens or set notepilot.ai.reasoning-effort, then retry.");
        }
        throw new AiServiceException("The model returned no final content (finish_reason="
                + finishReason + (reasoningLength > 0 ? ", only reasoning text was present" : "")
                + "). Please try again.");
    }

    // ------------------------------------------------------------------ JSON parsing and validation

    static String parseQuiz(String content) {
        Object root = parseJson(content);
        List<?> questions = requireList(root, "questions", QUIZ_COUNT);

        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            String label = "questions[" + i + "]";
            if (!(questions.get(i) instanceof Map<?, ?> q)) {
                throw invalid(label + " is not an object");
            }
            String question = requireText(q.get("question"), label + ".question");
            if (!seen.add(question.toLowerCase(Locale.ROOT))) {
                throw invalid(label + " duplicates another question");
            }
            if (!(q.get("options") instanceof Map<?, ?> rawOptions)) {
                throw invalid(label + ".options is missing");
            }
            Map<String, String> options = new LinkedHashMap<>();
            for (String letter : LETTERS) {
                options.put(letter, requireText(rawOptions.get(letter), label + ".options." + letter));
            }
            String answer = requireText(q.get("answer"), label + ".answer").toUpperCase(Locale.ROOT);
            if (!options.containsKey(answer)) {
                throw invalid(label + ".answer must be one of A, B, C, D");
            }
            String explanation = requireText(q.get("explanation"), label + ".explanation");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", question);
            item.put("options", options);
            item.put("answer", answer);
            item.put("explanation", explanation);
            normalized.add(item);
        }
        return MAPPER.writeValueAsString(Map.of("questions", normalized));
    }

    static String parseFlashcards(String content) {
        Object root = parseJson(content);
        List<?> cards = requireList(root, "flashcards", FLASHCARD_COUNT);

        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cards.size(); i++) {
            String label = "flashcards[" + i + "]";
            if (!(cards.get(i) instanceof Map<?, ?> c)) {
                throw invalid(label + " is not an object");
            }
            String question = requireText(c.get("question"), label + ".question");
            if (!seen.add(question.toLowerCase(Locale.ROOT))) {
                throw invalid(label + " duplicates another question");
            }
            String answer = requireText(c.get("answer"), label + ".answer");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", question);
            item.put("answer", answer);
            normalized.add(item);
        }
        return MAPPER.writeValueAsString(Map.of("flashcards", normalized));
    }

    private static Object parseJson(String content) {
        String json = stripCodeFences(content);
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (RuntimeException e) { // Jackson 3 exceptions are unchecked
            log.error("Model output is not valid JSON (length={}): {}", json.length(), truncate(e.getMessage(), 200));
            throw new AiServiceException("The AI returned malformed JSON. Please try again.", e);
        }
    }

    /** Removes a surrounding ```json ... ``` fence only if one is present. */
    static String stripCodeFences(String content) {
        String t = content.trim();
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            t = newline >= 0 ? t.substring(newline + 1) : t.substring(3);
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
            t = t.trim();
        }
        return t;
    }

    private static List<?> requireList(Object root, String field, int expectedSize) {
        if (!(root instanceof Map<?, ?> map) || !(map.get(field) instanceof List<?> list)) {
            throw invalid("missing '" + field + "' array");
        }
        if (list.size() != expectedSize) {
            throw invalid("expected exactly " + expectedSize + " items in '" + field + "' but got " + list.size());
        }
        return list;
    }

    private static String requireText(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        throw invalid(label + " is missing or empty");
    }

    private static AiServiceException invalid(String detail) {
        log.error("Invalid AI JSON structure: {}", detail);
        return new AiServiceException("The AI returned an unexpected format (" + detail + "). Please try again.");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
