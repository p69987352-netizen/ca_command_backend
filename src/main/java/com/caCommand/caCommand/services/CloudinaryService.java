package com.caCommand.caCommand.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(@Value("${cloudinary.cloud-name}") String cloudName,
                             @Value("${cloudinary.api-key}") String apiKey,
                             @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    public String uploadMedia(byte[] fileBytes, String fileName) {
        try {
            Map uploadResult = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                    "public_id", "ca_docs/" + fileName + "_" + System.currentTimeMillis(),
                    "resource_type", "auto" // Auto detects images/PDFs
            ));
            return (String) uploadResult.get("secure_url"); // Return secure Cloudinary URL
        } catch (Exception e) {
            System.out.println("❌ Cloudinary Upload Error: " + e.getMessage());
            return null;
        }
    }
}