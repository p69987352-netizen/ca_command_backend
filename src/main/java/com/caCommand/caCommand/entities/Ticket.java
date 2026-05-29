package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Data
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Ek client ke multiple tickets (kaam) ho sakte hain
    @ManyToOne 
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    // NAYA COLUMN ADD KAREIN
    private Double quotedFee;

    private String serviceType; // e.g., "ITR - Pending Finalization"

    private String status; // e.g., "PENDING_ADMIN_APPROVAL"

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp // NAYA FIELD ADD KAREIN
    private LocalDateTime updatedAt;

    // NAYA FIELD ADD KAREIN
    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff assignedStaff;

    @Column(columnDefinition = "text")
    private String clientDocuments;

    @Column(columnDefinition = "text")
    private String staffSubmittedDocument;

    @Column(name = "priority")
    private String priority = "Normal";

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}