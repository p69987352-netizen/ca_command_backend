package com.caCommand.caCommand.services;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LocationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public String getCityFromCoordinates(String lat, String lon) {
        // OpenStreetMap Free API URL
        String url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lon;

        try {
            // OpenStreetMap requires a User-Agent header, warna wo block kar dete hain
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "CACommandCenterBot/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode addressNode = rootNode.path("address");

            // API kabhi "city", kabhi "town", kabhi "state_district" bhejti hai
            if (addressNode.has("city")) {
                return addressNode.get("city").asText();
            } else if (addressNode.has("town")) {
                return addressNode.get("town").asText();
            } else if (addressNode.has("state_district")) {
                return addressNode.get("state_district").asText();
            } else if (addressNode.has("state")) {
                return addressNode.get("state").asText(); // Last fallback
            }

        } catch (Exception e) {
            log.warn("Location extraction failed for lat={} lon={}", lat, lon, e);
        }
        return null; // Agar fail ho jaye
    }
}
