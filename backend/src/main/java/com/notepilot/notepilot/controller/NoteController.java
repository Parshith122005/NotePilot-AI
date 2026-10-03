
package com.notepilot.notepilot.controller;

import com.notepilot.notepilot.model.NoteRequest;
import com.notepilot.notepilot.service.AiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class NoteController {

    private final AiService aiService;

    public NoteController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/hello")
    public String hello() {
        return "NotePilot AI backend is working!";
    }

    @PostMapping("/notes")
    public ResponseEntity<String> summarize(
            @RequestBody NoteRequest request) {

        if (request.getNotes() == null
                || request.getNotes().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Please provide your study notes.");
        }

        try {
            String summary =
                    aiService.generateSummary(request.getNotes());

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.BAD_GATEWAY
            ).body("AI request failed: " + e.getMessage());
        }
    }
}
