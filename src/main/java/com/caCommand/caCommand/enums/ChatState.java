package com.caCommand.caCommand.enums;

public enum ChatState {
    AWAITING_LOCATION,
    AWAITING_SERVICE_TYPE,
    AWAITING_SERVICE_SUBTYPE, // Naya state add kiya!
    AWAITING_DOCS,
    NEW,
    SERVICE_SELECTED,
    FINISHED,
    // 🌟 YEH DONO ADD KARNE HAIN
    AI_CONVERSATION,
}