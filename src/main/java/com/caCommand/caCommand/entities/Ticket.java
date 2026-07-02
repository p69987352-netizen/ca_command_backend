package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_status", columnList = "status"),
        @Index(name = "idx_tickets_client_status_created", columnList = "client_id,status,createdAt"),
        @Index(name = "idx_tickets_staff_status", columnList = "staff_id,status"),
        @Index(name = "idx_tickets_status_updated", columnList = "status,updatedAt"),
        @Index(name = "idx_tickets_credential_status", columnList = "credential_status")
})
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id")
    private Firm firm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private Double quotedFee;

    // Original card rate before discount
    private Double cardRateFee;

    // Discount % applied (based on PIN code tier + income)
    private Integer discountPercent = 0;

    @Column(nullable = false, length = 120)
    private String serviceType;

    // ITR form type: ITR-1, ITR-2, ITR-3, ITR-4, ITR-5, ITR-6
    @Column(length = 20)
    private String itrFormType;

    @Column(nullable = false, length = 40)
    private String status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff assignedStaff;

    @Column(unique = true, length = 20)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private com.caCommand.caCommand.enums.CaseStage caseStage;

    @Column(columnDefinition = "text")
    private String clientDocuments;

    @Column(columnDefinition = "text")
    private String staffSubmittedDocument;

    @Column(name = "ais_pdf_path", columnDefinition = "text")
    private String aisPdfPath;

    @Column(name = "priority")
    private String priority = "Normal";

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    @Column(name = "last_document_uploaded_at")
    private LocalDateTime lastDocumentUploadedAt;

    @Column(name = "pending_document_summary", nullable = false)
    private Boolean pendingDocumentSummary = false;

    @Column(name = "staff_update", columnDefinition = "TEXT")
    private String staffUpdate;

    @Column(name = "client_request_log", columnDefinition = "TEXT")
    private String clientRequestLog;

    @Column(name = "admin_staff_message", columnDefinition = "TEXT")
    private String adminStaffMessage;

    @Column(name = "revision_notes", columnDefinition = "TEXT")
    private String revisionNotes;

    @Column(name = "payment_link", columnDefinition = "TEXT")
    private String paymentLink;

    @Column(name = "payment_qr_url", columnDefinition = "TEXT")
    private String paymentQrUrl;

    @Column(name = "payment_reference_id", length = 120)
    private String paymentReferenceId;

    @Column(name = "payment_status", length = 40)
    private String paymentStatus = "NOT_CREATED";

    @Column(name = "payment_proof_url", columnDefinition = "TEXT")
    private String paymentProofUrl;

    @Column(name = "payment_completed_at")
    private LocalDateTime paymentCompletedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ======================================================
    // CREDENTIAL REQUEST SYSTEM
    // NONE → REQUESTED → RECEIVED
    // ======================================================
    @Column(name = "credential_status", length = 20)
    private String credentialStatus = "NONE";

    @Column(name = "credential_data", columnDefinition = "TEXT")
    private String credentialData;

    @Column(name = "credential_requested_at")
    private LocalDateTime credentialRequestedAt;

    @Column(name = "credential_received_at")
    private LocalDateTime credentialReceivedAt;

    @Column(name = "credential_request_label", length = 255)
    private String credentialRequestLabel;

    // ======================================================
    // CALL-BASED SERVICE FLOW (Audit, Advisory, Appeal, etc.)
    // ======================================================

    /** ITR / CALL_SERVICE / AUDIT / ADVISORY / APPEAL / FINANCIAL_PLANNING */
    @Column(name = "ticket_category", length = 40)
    private String ticketCategory = "ITR";

    @Column(name = "readiness_score")
    private Integer readinessScore;

    @Column(name = "pricing_analysis_id", length = 36)
    private String pricingAnalysisId;

    /** AIS/TIS report snapshot (JSON) at time of ticket creation */
    @Column(name = "ais_report_snapshot", columnDefinition = "TEXT")
    private String aisReportSnapshot;

    /** Notes from the call conversation */
    @Column(name = "call_notes", columnDefinition = "TEXT")
    private String callNotes;

    /** When the call was completed */
    @Column(name = "call_completed_at")
    private LocalDateTime callCompletedAt;

    // Pricing Override Audit Trail
    @Column(name = "admin_final_fee")
    private Double adminFinalFee;

    @Column(name = "admin_override_reason", columnDefinition = "TEXT")
    private String adminOverrideReason;

    @Column(name = "overridden_by", length = 120)
    private String overriddenBy;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;

    // ======================================================
    // PIPELINE EXECUTION TRACKING (Sprint 1.1)
    // ======================================================
    @Column(name = "pipeline_status", length = 40)
    private String pipelineStatus = com.caCommand.caCommand.enums.PipelineStatus.IDLE.name();

    @Column(name = "pipeline_execution_id", length = 36)
    private String pipelineExecutionId;

    @Column(name = "pipeline_version")
    private Integer pipelineVersion = 1;

    @Column(name = "pipeline_retry_count")
    private Integer pipelineRetryCount = 0;

    @Column(name = "pipeline_started_at")
    private LocalDateTime pipelineStartedAt;

    @Column(name = "pipeline_finished_at")
    private LocalDateTime pipelineFinishedAt;

    @Column(name = "pipeline_locked")
    private Boolean pipelineLocked = false;

    @Column(name = "has_pending_changes")
    private Boolean hasPendingChanges = false;

    @Column(name = "last_pipeline_error", columnDefinition = "TEXT")
    private String lastPipelineError;

}
