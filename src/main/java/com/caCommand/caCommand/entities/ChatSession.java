package com.caCommand.caCommand.entities;

import com.caCommand.caCommand.enums.ChatState;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions")
@lombok.EqualsAndHashCode(callSuper=false)
public class ChatSession extends BaseEntity {
    @Id
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private ChatState currentState;

    @Column(length = 255)
    private String extractedService;

    // Tracks which document types have been verified (CSV of canonical names)
    @Column(columnDefinition = "TEXT")
    private String verifiedDocumentTypes;

    @Column(length = 30)
    private String preferredLanguage = "HINGLISH";


    @Column(length = 500)
    private String collectingField;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(length = 60)
    private String selectedServiceCategory;

    // ──────────────────────────────────────────────────────
    // NEW: Identity & IT Portal fields
    // ──────────────────────────────────────────────────────

    @Column(length = 100)
    private String clientName;

    @Column(length = 100)
    private String clientCity;

    @Column(length = 20)
    private String clientPan;

    @Column(length = 50)
    private String clientDob;

    @Column(length = 100)
    private String clientItPassword;

    // ──────────────────────────────────────────────────────
    // Getters & Setters
    // ──────────────────────────────────────────────────────
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientCity() { return clientCity; }
    public void setClientCity(String clientCity) { this.clientCity = clientCity; }
    
    public String getClientPan() { return clientPan; }
    public void setClientPan(String clientPan) { this.clientPan = clientPan; }
    
    public String getClientDob() { return clientDob; }
    public void setClientDob(String clientDob) { this.clientDob = clientDob; }

    public String getClientItPassword() { return clientItPassword; }
    public void setClientItPassword(String clientItPassword) { this.clientItPassword = clientItPassword; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public ChatState getCurrentState() { return currentState; }
    public void setCurrentState(ChatState currentState) { this.currentState = currentState; }

    public String getExtractedService() { return extractedService; }
    public void setExtractedService(String extractedService) { this.extractedService = extractedService; }

    public String getVerifiedDocumentTypes() { return verifiedDocumentTypes; }
    public void setVerifiedDocumentTypes(String v) { this.verifiedDocumentTypes = v; }

    // Backward compat alias
    public String getDocumentMediaIds() { return verifiedDocumentTypes; }
    public void setDocumentMediaIds(String v) { this.verifiedDocumentTypes = v; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getCollectingField() { return collectingField; }
    public void setCollectingField(String collectingField) { this.collectingField = collectingField; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getSelectedServiceCategory() { return selectedServiceCategory; }
    public void setSelectedServiceCategory(String selectedServiceCategory) { this.selectedServiceCategory = selectedServiceCategory; }
}
