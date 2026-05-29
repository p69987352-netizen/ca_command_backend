package com.caCommand.caCommand.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppMessageSender {

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(String toPhoneNumber, String messageText) {
        // Meta ka official URL jahan message bhejna hai
        String url = "https://graph.facebook.com/v17.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        // JSON Payload banana
        Map<String, Object> textObject = new HashMap<>();
        textObject.put("preview_url", false);
        textObject.put("body", messageText);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", toPhoneNumber);
        body.put("type", "text");
        body.put("text", textObject);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            // Asli API Hit yahan ho rahi hai
            restTemplate.postForEntity(url, request, String.class);
            System.out.println("✅ Real Message Sent to WhatsApp: " + messageText);
        } catch (Exception e) {
            System.err.println("❌ Failed to send message: " + e.getMessage());
        }
    }
}