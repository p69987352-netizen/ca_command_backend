package com.caCommand.caCommand.services.ai;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroqProvider implements AIProviderService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    // Using Llama 3 models on Groq
    private static final String TEXT_MODEL = "llama-3.3-70b-versatile";
    private static final String VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GroqProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "GROQ";
    }

    @Override
    public String generateText(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", TEXT_MODEL);
        requestBody.put("temperature", 0.0);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        return executeGroqRequest(requestBody);
    }

    @Override
    public String generateTextFromImage(String prompt, String imageBase64) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", VISION_MODEL);
        requestBody.put("temperature", 0.0);
        
        String imageUrl = "data:image/jpeg;base64," + imageBase64;
        
        requestBody.put("messages", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        )
                )
        ));

        return executeGroqRequest(requestBody);
    }

    private String executeGroqRequest(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + groqApiKey);
        
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, requestEntity, String.class);
            return extractTextFromGroqResponse(response.getBody());
        } catch (Exception e) {
            log.error("Groq API Error", e);
            throw new RuntimeException("AI Provider failed", e);
        }
    }

    private String extractTextFromGroqResponse(String responseBody) {
        try {
            var rootNode = objectMapper.readTree(responseBody);
            return rootNode.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Error parsing Groq response", e);
            return "";
        }
    }
}
