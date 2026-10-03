package com.notepilot.notepilot.controller;

import com.notepilot.notepilot.model.NoteRequest;
import com.notepilot.notepilot.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api")
// "localhost" and "127.0.0.1" are different origins, so this must match the Live Server URL exactly.
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class NoteController {

    private static final Logger log = LoggerFactory.getLogger(NoteController.class);
    static final int MAX_NOTES_LENGTH = 20_000;

    private final AiService aiService;

    public NoteController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/hello")
    public String hello() {
        return "NotePilot AI backend is running.";
    }

    /** Returns {"summary": "..."} */
    @PostMapping("/notes")
    public ResponseEntity<Object> summary(@RequestBody(required = false) NoteRequest request) {
        return handle(request, "summary", notes -> {
            String text = aiService.generateSummary(notes);
            return Map.of("summary", text);
        });
    }

    /** Returns the validated quiz JSON: {"questions": [...]} */
    @PostMapping("/quiz")
    public ResponseEntity<Object> quiz(@RequestBody(required = false) NoteRequest request) {
        return handle(request, "quiz", aiService::generateQuiz);
    }

    /** Returns the validated flashcard JSON: {"flashcards": [...]} */
    @PostMapping("/flashcards")
    public ResponseEntity<Object> flashcards(@RequestBody(required = false) NoteRequest request) {
        return handle(request, "flashcards", aiService::generateFlashcards);
    }

    private ResponseEntity<Object> handle(NoteRequest request, String feature, Function<String, Object> action) {
        if (request == null || request.getNotes() == null || request.getNotes().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "Notes must not be empty.");
        }
        String notes = request.getNotes().trim();
        if (notes.length() > MAX_NOTES_LENGTH) {
            return error(HttpStatus.BAD_REQUEST,
                    "Notes are too long (max " + MAX_NOTES_LENGTH + " characters).");
        }
        try {
            Object body = action.apply(notes);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (AiService.AiServiceException e) {
            log.error("AI request failed for feature '{}': {}", feature, e.getMessage(), e);
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error for feature '{}'", feature, e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error. Check the backend logs.");
        }
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", message));
    }
}
