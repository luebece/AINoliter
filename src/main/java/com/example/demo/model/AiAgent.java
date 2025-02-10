package com.example.demo.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiAgent {
    private String id;
    private String role;
    private List<Map<String, Object>> conversationHistory;

    public AiAgent(String id, String role) {
        this.id = id;
        this.role = role;
        this.conversationHistory = new ArrayList<>();
    }

    public void addMessageToHistory(String role, String message) {
        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("role", role);
        historyEntry.put("parts", List.of(Map.of("text", message)));
        conversationHistory.add(historyEntry);
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<Map<String, Object>> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<Map<String, Object>> conversationHistory) {
        this.conversationHistory = conversationHistory;
    }
}