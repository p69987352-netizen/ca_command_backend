package com.caCommand.caCommand.enums;

public enum ChatState {
    // ── Legacy / existing states ──────────────────────────
    AWAITING_LOCATION,
    AWAITING_SERVICE_TYPE,
    AWAITING_SERVICE_SUBTYPE,
    AWAITING_DOCS,
    NEW,
    SERVICE_SELECTED,
    FINISHED,
    AI_CONVERSATION,

    // ── Identity & credential collection ──────────────────
    COLLECTING_NAME,          
    COLLECTING_CITY,          
    COLLECTING_PAN,           
    COLLECTING_DOB,           
    COLLECTING_PASSWORD,      

    // ── Service routing ───────────────────────────────────
    SERVICE_SELECTION_SHOWN,  
    AWAITING_CALL_SERVICE,    
    
    // ── Payment & Verification ────────────────────────────
    AWAITING_PAYMENT_PROOF,   
    AWAITING_ADMIN_VERIFICATION,

    // ── AI Response Modes ─────────────────────────────────
    ONBOARDING,
    DOCUMENT_COLLECTION,
    CUSTOM_DOCUMENT_REQUEST,
    CASE_UPDATE,
    GENERAL_QUERY
}