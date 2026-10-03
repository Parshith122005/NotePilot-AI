package com.notepilot.notepilot.model;

public class NoteRequest {

    private String notes;

    public NoteRequest() {
    }

    public NoteRequest(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
