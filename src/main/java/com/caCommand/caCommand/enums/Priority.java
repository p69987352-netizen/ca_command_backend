package com.caCommand.caCommand.enums;

public enum Priority {
    LOW,
    NORMAL,
    MEDIUM,
    HIGH,
    URGENT;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL.name();
        }

        try {
            return Priority.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            return NORMAL.name();
        }
    }
}
