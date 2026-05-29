package com.caCommand.caCommand.entities;

import com.caCommand.caCommand.enums.ChatState;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private ChatState currentState;

    // 🌟 NAYA NAAM: Isse purane 'temp_data' JSON column ka kissa hi khatam!
    @Column(length = 255)
    private String extractedService;

    @Column(columnDefinition = "TEXT")
    private String documentMediaIds;

    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and Setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public ChatState getCurrentState() { return currentState; }
    public void setCurrentState(ChatState currentState) { this.currentState = currentState; }

    // Naye Getters/Setters
    public String getExtractedService() { return extractedService; }
    public void setExtractedService(String extractedService) { this.extractedService = extractedService; }

    public String getDocumentMediaIds() { return documentMediaIds; }
    public void setDocumentMediaIds(String documentMediaIds) { this.documentMediaIds = documentMediaIds; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}