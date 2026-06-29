package com.caCommand.caCommand.dtos;

public record DocumentVerificationResult(
        boolean valid,
        String documentType,
        String reason
) {

    public static DocumentVerificationResult invalid(String reason) {
        return new DocumentVerificationResult(false, null, reason);
    }
}
