package com.notepilot.notepilot.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceTest {

    private static Map<String, Object> response(Object content, String finishReason) {
        Map<String, Object> message = new java.util.HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("reasoning_content", "thinking...");
        Map<String, Object> choice = new java.util.HashMap<>();
        choice.put("message", message);
        choice.put("finish_reason", finishReason);
        return Map.of("choices", List.of(choice));
    }

    @Test
    void extractsContent() {
        assertEquals("hello", AiService.extractContent(response("hello", "stop")));
    }

    @Test
    void nullContentWithLengthIsClearError() {
        var ex = assertThrows(AiService.AiServiceException.class,
                () -> AiService.extractContent(response(null, "length")));
        assertTrue(ex.getMessage().contains("max_tokens"));
    }

    @Test
    void nullResponseIsRejected() {
        assertThrows(AiService.AiServiceException.class, () -> AiService.extractContent(null));
    }

    @Test
    void stripsFences() {
        assertEquals("{\"a\":1}", AiService.stripCodeFences("```json\n{\"a\":1}\n```"));
    }

    @Test
    void flashcardsWithWrongCountAreRejected() {
        assertThrows(AiService.AiServiceException.class,
                () -> AiService.parseFlashcards("{\"flashcards\":[{\"question\":\"q\",\"answer\":\"a\"}]}"));
    }

    @Test
    void validFlashcardsPass() {
        StringBuilder sb = new StringBuilder("```json\n{\"flashcards\":[");
        for (int i = 0; i < 8; i++) {
            sb.append(i > 0 ? "," : "").append("{\"question\":\"q").append(i).append("\",\"answer\":\"a\"}");
        }
        sb.append("]}\n```");
        assertTrue(AiService.parseFlashcards(sb.toString()).contains("\"flashcards\""));
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(AiService.AiServiceException.class, () -> AiService.parseQuiz("not json"));
    }

    @Test
    void quizWithBadAnswerLetterIsRejected() {
        StringBuilder sb = new StringBuilder("{\"questions\":[");
        for (int i = 0; i < 5; i++) {
            sb.append(i > 0 ? "," : "").append("{\"question\":\"q").append(i)
              .append("\",\"options\":{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\",\"D\":\"4\"},")
              .append("\"answer\":\"E\",\"explanation\":\"x\"}");
        }
        sb.append("]}");
        assertThrows(AiService.AiServiceException.class, () -> AiService.parseQuiz(sb.toString()));
    }

    @Test
    void validQuizPasses() {
        StringBuilder sb = new StringBuilder("{\"questions\":[");
        for (int i = 0; i < 5; i++) {
            sb.append(i > 0 ? "," : "").append("{\"question\":\"q").append(i)
              .append("\",\"options\":{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\",\"D\":\"4\"},")
              .append("\"answer\":\"b\",\"explanation\":\"x\"}");
        }
        sb.append("]}");
        assertTrue(AiService.parseQuiz(sb.toString()).contains("\"answer\":\"B\""));
    }
}
