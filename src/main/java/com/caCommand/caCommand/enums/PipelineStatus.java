package com.caCommand.caCommand.enums;

public enum PipelineStatus {
    IDLE,
    QUEUED,
    LOCK_ACQUIRED,
    DOCUMENT_LOADING,
    VALIDATING,
    OCR_RUNNING,
    CLASSIFICATION,
    RULE_ENGINE,
    AI_ANALYSIS,
    PRICING,
    REPORT_GENERATION,
    NOTIFICATION,
    SUCCESS,
    FAILED,
    CANCELLED
}
