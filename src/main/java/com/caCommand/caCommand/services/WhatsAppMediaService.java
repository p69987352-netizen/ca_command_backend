package com.caCommand.caCommand.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


@Service
public class WhatsAppMediaService {

    @Value("${whatsapp.access-token}")
    private String accessToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 🌟 INJECT CLOUDINARY SERVICE
    private final CloudinaryService cloudinaryService;

    public WhatsAppMediaService(CloudinaryService cloudinaryService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.cloudinaryService = cloudinaryService;
    }

    public String downloadAndSaveMedia(String mediaId, String phoneNumber) {
        try {
            // 1. Get Media Download URL from Meta
            String urlEndpoint = "https://graph.facebook.com/v18.0/" + mediaId;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(urlEndpoint, HttpMethod.GET, entity, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String downloadUrl = rootNode.path("url").asText();

            // 2. Download the actual binary file (bytes) from Meta
            ResponseEntity<byte[]> fileResponse = restTemplate.exchange(downloadUrl, HttpMethod.GET, entity, byte[].class);
            byte[] fileBytes = fileResponse.getBody();

            if (fileBytes != null) {
                // 🌟 3. MAGIC: Upload directly to Cloudinary instead of saving to Laptop!
                String fileName = phoneNumber + "_" + mediaId; // Unique name
                String cloudinaryUrl = cloudinaryService.uploadMedia(fileBytes, fileName);

                System.out.println("☁️✅ File successfully uploaded to Cloudinary: " + cloudinaryUrl);

                // Return the LIVE web link so it saves in the database!
                return cloudinaryUrl;
            }

        } catch (Exception e) {
            System.err.println("❌ Error downloading or uploading media: " + e.getMessage());
        }
        return null;
    }
}