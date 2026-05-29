package com.caCommand.caCommand.services;


import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookProcessorService {

    private final ObjectMapper objectMapper;
    private final ChatBotService chatBotService; // 🌟 ADDED: ChatBotService injection

    // Constructor Injection
    public WebhookProcessorService(ChatBotService chatBotService) {
        this.objectMapper = new ObjectMapper();
        this.chatBotService = chatBotService;
    }

    @Async
    public void processIncomingMessage(String payload) {
        try {
            System.out.println("--- Async Thread Started ---");

            // String JSON ko Object Tree mein convert karna
            JsonNode rootNode = objectMapper.readTree(payload);

            // Navigate deep into the Meta JSON structure
            JsonNode entryNode = rootNode.path("entry").get(0);
            JsonNode changesNode = entryNode.path("changes").get(0);
            JsonNode valueNode = changesNode.path("value");

            // Meta sometimes sends status updates (like "Message Read"), we only want actual messages
            if (valueNode.has("messages")) {
                JsonNode messageNode = valueNode.path("messages").get(0);

                String fromPhoneNumber = messageNode.path("from").asText();
                String messageType = messageNode.path("type").asText(); // text, document, image, or location

                String messageContent = "";

                // 1. Handle Text
                if ("text".equals(messageType)) {
                    messageContent = messageNode.path("text").path("body").asText();
                }
                // 2. Handle Documents
                else if ("document".equals(messageType)) {
                    messageContent = messageNode.path("document").path("id").asText(); // Media ID
                }
                // 3. Handle Images
                else if ("image".equals(messageType)) {
                    messageContent = messageNode.path("image").path("id").asText(); // Media ID
                }
                // 🌟 4. Handle Location (NAYA FEATURE)
                else if ("location".equals(messageType)) {
                    String lat = messageNode.path("location").path("latitude").asText();
                    String lon = messageNode.path("location").path("longitude").asText();
                    messageContent = lat + "," + lon; // Dono ko jod kar ChatBotService ko bhejenge
                }

                System.out.println("📞 From: " + fromPhoneNumber + " | Type: " + messageType);
                System.out.println("💬 Content/MediaID/Location: " + messageContent);

                // Send to the Brain (ChatBotService)
                chatBotService.processUserMessage(fromPhoneNumber, messageType, messageContent);

            } else {
                System.out.println("ℹ️ Not a user message (might be a status update). Ignoring.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error parsing Meta JSON: " + e.getMessage());
        }
    }
}