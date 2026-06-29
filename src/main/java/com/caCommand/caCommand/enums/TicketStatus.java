package com.caCommand.caCommand.enums;

import java.util.Set;

public enum TicketStatus {
    NEW,
    WAITING_FOR_DOCUMENTS,
    READY_FOR_PROCESSING,
    QUEUED,
    OCR_RUNNING,
    CLASSIFICATION_RUNNING,
    RULE_ENGINE_RUNNING,
    AI_ANALYSIS,
    PRICING,
    ADMIN_REVIEW,
    PAYMENT_PENDING,
    PAYMENT_VERIFIED,
    CA_ASSIGNED,
    UNDER_REVIEW,
    COMPLETED,
    ARCHIVED,
    FAILED, // DLQ target
    
    // Legacy support to prevent immediate compilation errors in 90+ places, to be refactored
    PENDING_ADMIN_APPROVAL,
    AWAITING_PAYMENT,
    PAYMENT_VERIFICATION_PENDING,
    PAYMENT_RECEIVED,
    IN_PROGRESS,
    ASSIGNED_TO_STAFF,
    PENDING_ADMIN_QC,
    FINISHED,
    TRASH,
    CALL_PENDING,
    CALL_DONE,
    NORMAL_FLOW,
    WAITING_FOR_CLIENT_DOCUMENT,
    WAITING_FOR_ADMIN;

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            NEW.name(),
            WAITING_FOR_DOCUMENTS.name(),
            READY_FOR_PROCESSING.name(),
            QUEUED.name(),
            OCR_RUNNING.name(),
            CLASSIFICATION_RUNNING.name(),
            RULE_ENGINE_RUNNING.name(),
            AI_ANALYSIS.name(),
            PRICING.name(),
            ADMIN_REVIEW.name(),
            PAYMENT_PENDING.name(),
            PAYMENT_VERIFIED.name(),
            CA_ASSIGNED.name(),
            UNDER_REVIEW.name()
    );

    public static boolean isActive(String status) {
        return ACTIVE_STATUSES.contains(status) || 
               // Temp legacy support
               Set.of(PENDING_ADMIN_APPROVAL.name(), AWAITING_PAYMENT.name(), PAYMENT_VERIFICATION_PENDING.name(), PAYMENT_RECEIVED.name(), IN_PROGRESS.name(), ASSIGNED_TO_STAFF.name(), PENDING_ADMIN_QC.name(), CALL_PENDING.name(), NORMAL_FLOW.name(), WAITING_FOR_CLIENT_DOCUMENT.name(), WAITING_FOR_ADMIN.name()).contains(status);
    }
}
