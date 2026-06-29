package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.security.WhatsAppSecurityUtils;
import com.caCommand.caCommand.services.WebhookProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final String verifyToken;
    private final WhatsAppSecurityUtils securityUtils;
    private final WebhookProcessorService processorService;

    public WhatsAppWebhookController(
            @Value("${whatsapp.verify-token}") String verifyToken,
            WhatsAppSecurityUtils securityUtils,
            WebhookProcessorService processorService
    ) {
        this.verifyToken = verifyToken;
        this.securityUtils = securityUtils;
        this.processorService = processorService;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Meta WhatsApp webhook verified");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Rejected WhatsApp webhook verification request with mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receiveMessage(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader
    ) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Rejected unsigned WhatsApp webhook request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!securityUtils.isValidSignature(payload, signatureHeader)) {
            log.warn("Rejected WhatsApp webhook request with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        processorService.processIncomingMessage(payload);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
