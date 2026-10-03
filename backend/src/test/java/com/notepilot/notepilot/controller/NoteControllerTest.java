package com.notepilot.notepilot.controller;

import com.notepilot.notepilot.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteControllerTest {

    private AiService aiService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        mvc = MockMvcBuilders.standaloneSetup(new NoteController(aiService)).build();
    }

    @Test
    void helloReturnsOk() throws Exception {
        mvc.perform(get("/api/hello")).andExpect(status().isOk());
    }

    @Test
    void blankNotesAreRejected() throws Exception {
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(aiService);
    }

    @Test
    void missingBodyIsRejected() throws Exception {
        mvc.perform(post("/api/quiz")).andExpect(status().isBadRequest());
        verifyNoInteractions(aiService);
    }

    @Test
    void summaryReturnsSummaryField() throws Exception {
        when(aiService.generateSummary("my notes")).thenReturn("# Title");
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"my notes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("# Title"));
    }

    @Test
    void flashcardsReturnRawJson() throws Exception {
        when(aiService.generateFlashcards("my notes")).thenReturn("{\"flashcards\":[]}");
        mvc.perform(post("/api/flashcards").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"my notes\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"flashcards\":[]}"));
    }

    @Test
    void aiFailureReturns502() throws Exception {
        when(aiService.generateQuiz("my notes")).thenThrow(new AiService.AiServiceException("boom"));
        mvc.perform(post("/api/quiz").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"my notes\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("boom"));
    }
}
