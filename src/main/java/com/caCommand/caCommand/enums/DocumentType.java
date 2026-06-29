package com.caCommand.caCommand.enums;

public enum DocumentType {
    AIS("AIS Statement (PDF)"),
    TIS("TIS Statement (PDF)"),
    FORM16("Form 16"),
    FORM26AS("Form 26AS"),
    NOTICE("Notice PDF"),
    PAN("PAN Card"),
    AADHAAR("Aadhar Card"),
    BANK_STATEMENT("Bank Statement (Last 1 Year)"),
    UNKNOWN("Unknown");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DocumentType fromString(String text) {
        if (text == null || text.isBlank()) return UNKNOWN;
        for (DocumentType type : values()) {
            if (type.name().equalsIgnoreCase(text) || type.displayName.equalsIgnoreCase(text)) {
                return type;
            }
        }
        // Fuzzy matching for common variations
        String lower = text.toLowerCase();
        if (lower.contains("ais")) return AIS;
        if (lower.contains("tis")) return TIS;
        if (lower.contains("form 16") || lower.contains("form16")) return FORM16;
        if (lower.contains("26as") || lower.contains("form 26")) return FORM26AS;
        if (lower.contains("pan")) return PAN;
        if (lower.contains("aadh") || lower.contains("aadhaar")) return AADHAAR;
        if (lower.contains("bank")) return BANK_STATEMENT;
        if (lower.contains("notice")) return NOTICE;
        return UNKNOWN;
    }
    
    public boolean requiresPassword() {
        return this == AIS || this == TIS;
    }
}
