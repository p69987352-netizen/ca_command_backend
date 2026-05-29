package com.caCommand.caCommand.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppSecurityUtils {

    @Value("${whatsapp.app-secret}")
    private String appSecret;

public boolean isValidSignature(String payload, String signatureHeader) {
    if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
        System.out.println("DEBUG: Missing or malformed header -> " + signatureHeader);
        return false;
    }

    String expectedSignature = "sha256=" + calculateHmacSha256(payload, appSecret);
    
    // --- DEBUG LOGS (Temporary) ---
    System.out.println("========== DEBUG INFO ==========");
    System.out.println("Java Expected : " + expectedSignature);
    System.out.println("Postman Sent  : " + signatureHeader);
    System.out.println("================================");
    
    return expectedSignature.equals(signatureHeader);
}
    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Convert byte array to Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }
}