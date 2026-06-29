package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.common.exceptions.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
public class ValidationService {

    public String generateSha256(byte[] documentBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(documentBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("Could not hash document", e);
        }
    }

    public void validateOcrQuality(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            throw new ValidationException("Blank OCR: The document appears to be empty or unscannable.");
        }
        if (ocrText.length() < 50) {
            log.warn("OCR text is very short ({} chars). Might be a corrupted scan.", ocrText.length());
        }
    }
}
