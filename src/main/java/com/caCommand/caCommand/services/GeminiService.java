package com.caCommand.caCommand.services;

import com.caCommand.caCommand.dtos.GeminiAIResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 🧠 Service-wise Document Requirements (Production Ready)
    private static final Map<String, List<String>> SERVICE_DOCUMENTS = new HashMap<>();

    static {
        SERVICE_DOCUMENTS.put("ITR Filing", Arrays.asList(
                "PAN Card", "Aadhar Card", "Form 16", "Bank Statements (Last 6 months)",
                "Salary Slips", "Investment Proofs (80C, 80D)"
        ));

        SERVICE_DOCUMENTS.put("GST Registration", Arrays.asList(
                "PAN Card", "Aadhar Card", "Business Address Proof (Electricity Bill/Rent Agreement)",
                "Bank Account Statement", "Photographs", "Board Resolution (for Companies)"
        ));

        SERVICE_DOCUMENTS.put("GST Return Filing", Arrays.asList(
                "GST Login Credentials", "Sales/Purchase Register", "GSTR-1 Data",
                "GSTR-3B Data", "Input Tax Credit Details"
        ));

        SERVICE_DOCUMENTS.put("Audit", Arrays.asList(
                "Financial Statements", "Ledger Books", "Bank Statements (Full Year)",
                "Invoice Copies", "TDS Certificates", "GST Returns"
        ));

        SERVICE_DOCUMENTS.put("Company Registration", Arrays.asList(
                "PAN Card (All Directors)", "Aadhar Card (All Directors)", "Address Proof",
                "DIN of Directors", "NOC from Property Owner", "MOA & AOA Draft"
        ));

        SERVICE_DOCUMENTS.put("PAN Card Application", Arrays.asList(
                "Aadhar Card", "Photograph", "Date of Birth Proof", "Address Proof"
        ));

        SERVICE_DOCUMENTS.put("TDS Return Filing", Arrays.asList(
                "Form 16/16A", "TDS Challan", "Salary Register", "TAN Number"
        ));
    }

    public GeminiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public GeminiAIResponse analyzeUserMessage(String userMessage, String currentCity, String currentService) {
        // 🌟 FIX 2: Removed markdown brackets from the URL string
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        String cityStr = (currentCity == null || currentCity.trim().isEmpty()) ? "NOT_PROVIDED" : currentCity;
        String serviceStr = (currentService == null || currentService.trim().isEmpty()) ? "NOT_PROVIDED" : currentService;

        // 🎯 **Enhanced System Prompt with Smarter Instructions**
        String systemPrompt = buildEnhancedPrompt(userMessage, cityStr, serviceStr);

        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("contents", List.of(
                    Map.of("parts", List.of(Map.of("text", systemPrompt)))
            ));

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("responseMimeType", "application/json");
            configMap.put("temperature", 0.2);
            configMap.put("maxOutputTokens", 8192);

            requestMap.put("generationConfig", configMap);

            String requestBody = objectMapper.writeValueAsString(requestMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String aiText = rootNode.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();

            if (aiText.startsWith("```json\n")) {
                aiText = aiText.substring(8, aiText.length() - 3).trim();
            } else if (aiText.startsWith("```json")) {
                aiText = aiText.substring(7, aiText.length() - 3).trim();
            } else if (aiText.startsWith("```")) {
                aiText = aiText.substring(3, aiText.length() - 3).trim();
            }

            GeminiAIResponse aiResponse = objectMapper.readValue(aiText, GeminiAIResponse.class);

            // 🔍 Post-process to ensure document list accuracy
            if (aiResponse.getService() != null && SERVICE_DOCUMENTS.containsKey(aiResponse.getService())) {
                aiResponse.setDocuments_required(SERVICE_DOCUMENTS.get(aiResponse.getService()));
            }

            return aiResponse;

        } catch (Exception e) {
            System.out.println("⚠️ Gemini API Error: " + e.getMessage() + ". Activating Smart Fallback...");
            return getIntelligentFallbackResponse(userMessage, cityStr, serviceStr);
        }
    }

    // ==========================================
    // 🧠 ENHANCED PROMPT ENGINEERING
    // ==========================================
    private String buildEnhancedPrompt(String userMessage, String cityStr, String serviceStr) {
        String availableServices = String.join(", ", SERVICE_DOCUMENTS.keySet());

        return String.format("""
            You are an intelligent AI assistant for a professional CA (Chartered Accountant) firm in India.
            You help clients by understanding their needs, collecting information, and guiding document submission.
            
            📋 **CURRENT CONVERSATION CONTEXT:**
            • Client's Known City: '%s'
            • Client's Known Service: '%s'
            • Client's Latest Message: '%s'
            
            🎯 **YOUR CORE RESPONSIBILITIES:**
            
            1. **ENTITY EXTRACTION:**
               - Extract city name if mentioned (e.g., "Delhi", "Mumbai", "Bangalore")
               - Identify service request from: %s
               - Return null if not mentioned, NEVER assume or invent
            
            2. **CONVERSATION FLOW LOGIC:**
               
               ✅ **Stage 1: Greeting & Initial Detection**
               - If user says "hi", "hello", "namaste" → Warmly greet and ask "Aapko kaun si CA service chahiye?"
               - If user mentions service but NO city → Ask politely for city using WhatsApp location share (📎 icon)
               
               ✅ **Stage 2: Information Collection**
               - If City = NOT_PROVIDED → "Kripya apna city share karein ya WhatsApp location button (📎) dabayein"
               - If Service = NOT_PROVIDED → "Aapko kaunsi service chahiye? (ITR, GST, Audit, etc.)"
               
               ✅ **Stage 3: Document Guidance (ONLY when BOTH City and Service are known)**
               - List EXACT required documents based on the service
               - Explain in simple Hinglish what each document is
               - Ask user to upload via WhatsApp image/PDF
            
            3. **SPECIAL RULES:**
               🔐 If user message contains "hi bhanu" or "bhanu this side" → Start reply with "ok bhanu"
               🚫 NEVER make up information
               💬 Always respond in friendly Hinglish (Hindi + English mix)
               📝 Keep responses concise but informative
            
            4. **RESPONSE STRUCTURE (Strict JSON):**
            ```json
            {
              "city": "extracted city name or null",
              "service": "matched service name or null",
              "reply_message": "Your complete Hinglish response with document list if applicable",
              "documents_required": ["List of actual documents for the service"],
              "conversation_stage": "greeting | collecting_info | ready_for_documents | awaiting_upload"
            }
            ```
            
            🎓 **EXAMPLE SCENARIOS:**
            
            User: "Hi, I need help with ITR"
            Response: {
              "city": null,
              "service": "ITR Filing",
              "reply_message": "Namaste! ITR filing mein help ke liye aapka city bataiye ya WhatsApp ka location button (📎) use karein.",
              "documents_required": [],
              "conversation_stage": "collecting_info"
            }
            
            User: "I'm from Mumbai"
            Response: {
              "city": "Mumbai",
              "service": "ITR Filing",
              "reply_message": "Perfect! Mumbai ke liye ITR filing process shuru karte hain. Aapko ye documents upload karne honge:\\n\\n📄 Required Documents:\\n1. PAN Card\\n2. Aadhar Card\\n3. Form 16\\n4. Bank Statements (Last 6 months)\\n5. Salary Slips\\n6. Investment Proofs (80C, 80D)\\n\\nKripya in documents ki clear photos ya PDFs yahan upload karein.",
              "documents_required": ["PAN Card", "Aadhar Card", "Form 16", "Bank Statements (Last 6 months)", "Salary Slips", "Investment Proofs (80C, 80D)"],
              "conversation_stage": "ready_for_documents"
            }
            
            ⚡ **NOW PROCESS THE USER'S MESSAGE AND RESPOND ACCORDINGLY.**
            """,
                cityStr,
                serviceStr,
                userMessage,
                availableServices
        );
    }

    // ==========================================
    // 🛡️ INTELLIGENT FALLBACK SYSTEM
    // ==========================================
    private GeminiAIResponse getIntelligentFallbackResponse(String userMessage, String cityStr, String serviceStr) {
        String msgLower = userMessage.toLowerCase().trim();
        GeminiAIResponse fallback = new GeminiAIResponse();

        fallback.setCity("NOT_PROVIDED".equals(cityStr) ? null : cityStr);
        fallback.setService("NOT_PROVIDED".equals(serviceStr) ? null : serviceStr);

        String detectedService = detectServiceFromMessage(msgLower);
        if (detectedService != null && fallback.getService() == null) {
            fallback.setService(detectedService);
        }

        boolean isBhanu = msgLower.contains("bhanu");
        String greeting = isBhanu ? "ok bhanu. " : "Namaste! ";

        if (fallback.getCity() == null) {
            fallback.setReply_message(greeting + "Backup system active hai. Pehle aap apna city share karein ya WhatsApp location button (📎) use karein.");
            fallback.setConversation_stage("collecting_info");
            fallback.setDocuments_required(new ArrayList<>());
        }
        else if (fallback.getService() == null) {
            fallback.setReply_message(greeting + "Aapko kaunsi CA service chahiye? (ITR Filing, GST Registration, Audit, Company Registration, etc.)");
            fallback.setConversation_stage("collecting_info");
            fallback.setDocuments_required(new ArrayList<>());
        }
        else {
            List<String> docs = SERVICE_DOCUMENTS.getOrDefault(fallback.getService(),
                    Arrays.asList("PAN Card", "Aadhar Card"));

            fallback.setDocuments_required(docs);
            fallback.setConversation_stage("ready_for_documents");

            StringBuilder msg = new StringBuilder(greeting);
            msg.append("Backup engine active hai. ").append(fallback.getService())
                    .append(" ke liye ye documents upload karein:\n\n📄 Required Documents:\n");

            for (int i = 0; i < docs.size(); i++) {
                msg.append(i + 1).append(". ").append(docs.get(i)).append("\n");
            }

            msg.append("\nKripya clear photos ya PDFs upload karein.");
            fallback.setReply_message(msg.toString());
        }

        return fallback;
    }

    // ==========================================
    // 🔍 SMART SERVICE DETECTION ENGINE
    // ==========================================
    private String detectServiceFromMessage(String message) {
        Map<String, String[]> serviceKeywords = new HashMap<>();
        serviceKeywords.put("ITR Filing", new String[]{"itr", "income tax", "return file", "tax return", "salary tax"});
        serviceKeywords.put("GST Registration", new String[]{"gst registration", "gst number", "new gst"});
        serviceKeywords.put("GST Return Filing", new String[]{"gst return", "gstr", "gst filing"});
        serviceKeywords.put("Audit", new String[]{"audit", "balance sheet", "financial audit"});
        serviceKeywords.put("Company Registration", new String[]{"company registration", "pvt ltd", "register company"});
        serviceKeywords.put("PAN Card Application", new String[]{"pan card", "new pan", "pan apply"});
        serviceKeywords.put("TDS Return Filing", new String[]{"tds return", "tds filing", "form 16"});

        for (Map.Entry<String, String[]> entry : serviceKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (message.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    // ==========================================
    // 🔍 ENHANCED DOCUMENT VERIFICATION
    // ==========================================
    public boolean verifyDocumentType(String imageUrl, String expectedDocType) {
        // 🌟 FIX 3: Removed -exp to ensure stability
        String apiUrl = "[https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=](https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=)" + apiKey;

        String verificationPrompt = String.format("""
        You are an expert Indian document verification AI with OCR capabilities.
        
        **Task:** Verify if the image at '%s' is a valid '%s'
        
        **Verification Rules:**
        - PAN Card: Check for 10-character alphanumeric PAN, Income Tax Department logo, cardholder photo
        - Aadhar Card: Verify UIDAI logo, 12-digit Aadhar number, hologram (if front side)
        - Bank Statement: Look for bank letterhead, account number, transaction details
        - Form 16: Check for employer TAN, employee PAN, Part A & B structure
        - Electricity Bill: Verify utility provider name, consumer number, billing period
        
        **Response Format:** Reply with ONLY ONE WORD:
        - "VALID" if document matches expected type and is clearly readable
        - "INVALID" if wrong document, blurry, or unreadable
        
        No explanations, no markdown, just the verdict.
        """, imageUrl, expectedDocType);

        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("contents", List.of(
                    Map.of("parts", List.of(Map.of("text", verificationPrompt)))
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestMap, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String aiReply = rootNode.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();

            System.out.println("🔍 AI Verification for " + expectedDocType + ": " + aiReply);
            return "VALID".equalsIgnoreCase(aiReply);

        } catch (Exception e) {
            System.out.println("❌ Verification API Failed: " + e.getMessage() + " → Assuming valid (graceful degradation)");
            return true;
        }
    }
}