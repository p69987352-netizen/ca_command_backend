package com.caCommand.caCommand.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppSecurityUtilsTest {

    @Test
    void acceptsValidSignature() throws Exception {
        WhatsAppSecurityUtils securityUtils = new WhatsAppSecurityUtils("secret");
        String payload = "{\"entry\":[]}";

        assertThat(securityUtils.isValidSignature(payload, signature(payload, "secret"))).isTrue();

    }

    @Test
    void rejectsInvalidSignature() {
        WhatsAppSecurityUtils securityUtils = new WhatsAppSecurityUtils("secret");

        assertThat(securityUtils.isValidSignature("{\"entry\":[]}", "sha256=bad")).isFalse();
    }

    @Test
    void rejectsMalformedSignatureHeader() {
        WhatsAppSecurityUtils securityUtils = new WhatsAppSecurityUtils("secret");

        assertThat(securityUtils.isValidSignature("{}", "bad")).isFalse();
    }

    private String signature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
