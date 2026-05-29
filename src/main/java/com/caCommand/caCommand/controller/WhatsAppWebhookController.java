package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.security.WhatsAppSecurityUtils;
import com.caCommand.caCommand.services.WebhookProcessorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook/whatsapp")
public class WhatsAppWebhookController {

    // Ab token application.properties se aayega
    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    private final WhatsAppSecurityUtils securityUtils;
    private final WebhookProcessorService processorService;

    public WhatsAppWebhookController(WhatsAppSecurityUtils securityUtils, WebhookProcessorService processorService) {
        this.securityUtils = securityUtils;
        this.processorService = processorService;
    }

    /**
     * 1. GET Request: Meta uses this to verify your URL initially.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        // Hardcoded string ki jagah hum directly properties wali value (verifyToken) use kar rahe hain
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            System.out.println("✅ Meta Webhook Verified Successfully!");
            return ResponseEntity.ok(challenge);
        } else {
            return ResponseEntity.status(403).body("Verification failed");
        }
    }

    /**
     * 2. POST Request: Meta sends all user messages (Hi, Location, Docs) here.
     */
    @PostMapping
    public ResponseEntity<String> receiveMessage(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader) {

        // --- SECURITY CHECK ---
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            System.out.println("⚠️ WARNING: Bypassing Security for Local Postman Testing!");
        } else if (!securityUtils.isValidSignature(payload, signatureHeader)) {
            System.err.println("ALERT: Invalid Signature detected!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // ------------------------------

        // Step B: Pass payload to Background Thread (Async)
        processorService.processIncomingMessage(payload);

        // Step C: IMMEDIATELY return 200 OK to Meta (within milliseconds)
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}