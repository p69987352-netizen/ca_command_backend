package com.caCommand.caCommand.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WhatsAppMessageSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageSender.class);

    private final String accessToken;
    private final String phoneNumberId;
    private final RestTemplate restTemplate;

    public WhatsAppMessageSender(
            @Value("${whatsapp.access-token}") String accessToken,
            @Value("${whatsapp.phone-number-id}") String phoneNumberId,
            RestTemplate restTemplate
    ) {
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
        this.restTemplate = restTemplate;
    }

    public void sendMessage(String toPhoneNumber, String messageText) {
        // Meta ka official URL jahan message bhejna hai
        String url = "https://graph.facebook.com/v21.0/" + phoneNumberId + "/messages";

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
            log.info("WhatsApp message sent to={}", toPhoneNumber);
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp message to={}", toPhoneNumber, e);
        }
    }

    public void sendImageMessage(String toPhoneNumber, String imageUrl, String caption) {
        String url = "https://graph.facebook.com/v21.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> imageObject = new HashMap<>();
        imageObject.put("link", imageUrl);
        imageObject.put("caption", caption);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", toPhoneNumber);
        body.put("type", "image");
        body.put("image", imageObject);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("WhatsApp image message sent to={}", toPhoneNumber);
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp image message to={}", toPhoneNumber, e);
        }
    }

    public void sendTemplateMessage(String toPhoneNumber, String templateName, String languageCode, List<String> textParams) {
        String url = "https://graph.facebook.com/v21.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> language = new HashMap<>();
        language.put("code", languageCode);

        List<Map<String, Object>> components = new ArrayList<>();
        
        if (textParams != null && !textParams.isEmpty()) {
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (String param : textParams) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("type", "text");
                paramMap.put("text", param);
                parameters.add(paramMap);
            }
            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", parameters);
            components.add(bodyComponent);
        }

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        template.put("language", language);
        if (!components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "template");
        body.put("template", template);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("WhatsApp template message sent to={} (Template: {})", toPhoneNumber, templateName);
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp template message to={}", toPhoneNumber, e);
        }
    }
}
