package com.caCommand.caCommand.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


@Service
public class WhatsAppMediaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMediaService.class);

    private final String accessToken;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final S3StorageService S3StorageService;

    public WhatsAppMediaService(
            @Value("${whatsapp.access-token}") String accessToken,
            RestTemplate restTemplate,
            S3StorageService S3StorageService
    ) {
        this.accessToken = accessToken;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.S3StorageService = S3StorageService;
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
                String s3Url = S3StorageService.uploadMedia(fileBytes, fileName);

                log.info("Uploaded WhatsApp media to Cloudinary for phone={} mediaId={}", phoneNumber, mediaId);

                // Return the LIVE web link so it saves in the database!
                return s3Url;
            }

        } catch (Exception e) {
            log.warn("Failed to download or upload WhatsApp media mediaId={}", mediaId, e);
        }
        return null;
    }
}
