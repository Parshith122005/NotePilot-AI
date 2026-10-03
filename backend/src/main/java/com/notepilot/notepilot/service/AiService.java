
package com.notepilot.notepilot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${HF_TOKEN}")
    private String hfToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateSummary(String notes) {

        String url =
                "https://router.huggingface.co/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hfToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "model", "deepseek-ai/DeepSeek-V4.1-Flash",
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are NotePilot AI, a study assistant. "
                                        + "Summarize the student's notes in simple language. "
                                        + "Use clear bullet points and preserve important facts."
                        ),
                        Map.of(
                                "role", "user",
                                "content", notes
                        )
                ),
                "max_tokens", 2000,
                "stream", false
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        Map<?, ?> response = restTemplate.postForObject(
                url, request, Map.class
        );

        if (response == null) {
            throw new RuntimeException("No response received from AI");
        }


        Map<?, ?> choice = (Map<?, ?>)
                ((List<?>) response.get("choices")).get(0);

        Map<?, ?> message = (Map<?, ?>) choice.get("message");

        if (message == null) {
            throw new RuntimeException(
                    "AI response has no message: " + response
            );
        }

        Object content = message.get("content");

        if (content == null) {
            throw new RuntimeException(
                    "AI returned no summary text. Finish reason: "
                            + choice.get("finish_reason")
            );
        }

        return content.toString();

    }
}
