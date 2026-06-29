package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "custom_document_requests")
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class CustomDocumentRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "status")
    private String status = "PENDING"; // PENDING, FULFILLED, REJECTED

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;
}
