package com.caCommand.caCommand.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessorService.class);

    private final ObjectMapper objectMapper;
    private final ChatBotService chatBotService;

    public WebhookProcessorService(ChatBotService chatBotService) {
        this.objectMapper = new ObjectMapper();
        this.chatBotService = chatBotService;
    }

    private final java.util.Set<String> processedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Async
    public void processIncomingMessage(String payload) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            JsonNode valueNode = rootNode.path("entry").path(0).path("changes").path(0).path("value");

            if (!valueNode.has("messages")) {
                log.debug("Ignoring WhatsApp status webhook without user messages");
                return;
            }

            JsonNode messageNode = valueNode.path("messages").path(0);
            String messageId = messageNode.path("id").asText();
            
            // Webhook Idempotency Check
            if (messageId != null && !messageId.isBlank() && !processedMessageIds.add(messageId)) {
                log.info("Duplicate Webhook ignored for messageId={}", messageId);
                return;
            }

            // Simple cleanup to prevent OOM
            if (processedMessageIds.size() > 10000) {
                processedMessageIds.clear();
            }

            String fromPhoneNumber = messageNode.path("from").asText();
            String messageType = messageNode.path("type").asText();
            String messageContent = extractMessageContent(messageNode, messageType);

            if (fromPhoneNumber.isBlank() || messageType.isBlank()) {
                log.warn("Ignoring malformed WhatsApp message webhook");
                return;
            }

            log.info("Processing WhatsApp message from={} type={} id={}", fromPhoneNumber, messageType, messageId);
            chatBotService.processUserMessage(fromPhoneNumber, messageType, messageContent);
        } catch (Exception e) {
            log.error("Failed to process WhatsApp webhook payload", e);
        }
    }

    private String extractMessageContent(JsonNode messageNode, String messageType) {
        return switch (messageType) {
            case "text" -> messageNode.path("text").path("body").asText();
            case "document" -> {
                String id = messageNode.path("document").path("id").asText();
                String filename = messageNode.path("document").path("filename").asText("");
                yield id + "|" + filename;
            }
            case "image" -> messageNode.path("image").path("id").asText();
            case "location" -> messageNode.path("location").path("latitude").asText()
                    + "," + messageNode.path("location").path("longitude").asText();
            default -> "";
        };
    }
}
