package com.caCommand.caCommand.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class WhatsAppSecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSecurityUtils.class);
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String appSecret;

    public WhatsAppSecurityUtils(@Value("${whatsapp.app-secret:}") String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean isValidSignature(String payload, String signatureHeader) {
        if (appSecret == null || appSecret.isBlank()) {
            log.error("WhatsApp App Secret is not configured. Webhook signature validation failed.");
            return false;
        }
        if (payload == null || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String expectedSignature = SIGNATURE_PREFIX + calculateHmacSha256(payload, appSecret);
        boolean valid = constantTimeEquals(expectedSignature, signatureHeader);

        if (!valid) {
            log.warn("WhatsApp webhook signature validation failed");
        }

        return valid;
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate WhatsApp webhook signature", e);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        try {
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    actual.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            if (ex instanceof NoSuchAlgorithmException) {
                log.error("Required digest algorithm is unavailable", ex);
            }
            return false;
        }
    }
}
