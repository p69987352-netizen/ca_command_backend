package com.caCommand.caCommand.services;

import com.caCommand.caCommand.dtos.GeminiAIResponse;
import com.caCommand.caCommand.dtos.DocumentVerificationResult;
import com.caCommand.caCommand.dtos.AisData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=";
    private static final String TEXT_MODEL = "gemini-3.5-flash";
    private static final String VISION_MODEL = "gemini-3.5-flash";

    private final com.caCommand.caCommand.services.ai.AIProviderService aiProviderService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final S3StorageService S3StorageService;

    // ============================================================
    // SERVICE → REQUIRED DOCUMENTS MAP
    // ============================================================
    public static final Map<String, List<String>> SERVICE_DOCUMENTS = new HashMap<>();

    static {
        SERVICE_DOCUMENTS.put("ITR Filing", Arrays.asList(
                "PAN Card", "Aadhar Card", "AIS Statement (PDF)", "TIS Statement (PDF)", "Form 26AS", "Bank Statement (Last 1 Year)"
        ));
        SERVICE_DOCUMENTS.put("Tax Notice / Appeal", Arrays.asList(
                "Notice PDF", "Assessment Order", "Previous ITR", "Previous Response Submitted"
        ));
        SERVICE_DOCUMENTS.put("GST Services", Arrays.asList(
                "GST Certificate", "GSTR-1", "GSTR-3B", "Sales Report", "Purchase Report"
        ));
        SERVICE_DOCUMENTS.put("Audit & Compliance", Arrays.asList(
                "Financial Statements", "Trial Balance", "Profit & Loss Statement", "Balance Sheet", "GST Returns", "Previous Audit Report"
        ));
        SERVICE_DOCUMENTS.put("Financial Planning", Arrays.asList(
                "Income Details", "Existing Investments", "Insurance Details", "Loan Details", "Bank Statements"
        ));
        SERVICE_DOCUMENTS.put("Business Registration", Arrays.asList(
                "PAN Card", "Aadhar Card", "Passport Size Photo", "Mobile Number", "Email ID", "Business Address Proof"
        ));
        SERVICE_DOCUMENTS.put("Tax Advisory", Arrays.asList(
                "Query Description"
        ));
        SERVICE_DOCUMENTS.put("Legal Assistance (Arjun AI)", Arrays.asList(
                "Legal Notice / Order", "Relevant Agreements", "Supporting Documents"
        ));
    }

    // ============================================================
    // CANONICAL DOCUMENT NAME ALIASES
    // Normalizes AI output variations to exact required document names
    // ============================================================
    private static final Map<String, String> DOCUMENT_ALIASES = new HashMap<>();

    static {
        // Aadhar variations
        DOCUMENT_ALIASES.put("aadhaar card", "Aadhar Card");
        DOCUMENT_ALIASES.put("aadhar", "Aadhar Card");
        DOCUMENT_ALIASES.put("aadhaar", "Aadhar Card");
        DOCUMENT_ALIASES.put("aadhar card", "Aadhar Card");
        DOCUMENT_ALIASES.put("aadhaar id", "Aadhar Card");
        DOCUMENT_ALIASES.put("uid", "Aadhar Card");

        // PAN variations
        DOCUMENT_ALIASES.put("pan card", "PAN Card");
        DOCUMENT_ALIASES.put("pan", "PAN Card");
        DOCUMENT_ALIASES.put("permanent account number", "PAN Card");
        DOCUMENT_ALIASES.put("income tax pan", "PAN Card");

        // Form 16 variations
        DOCUMENT_ALIASES.put("form 16", "Form 16");
        DOCUMENT_ALIASES.put("form16", "Form 16");
        DOCUMENT_ALIASES.put("form 16a", "Form 16 / Form 16A");
        DOCUMENT_ALIASES.put("tds certificate", "Form 16 / Form 16A");
        DOCUMENT_ALIASES.put("tds certificates", "TDS Certificates");

        // AIS variations
        DOCUMENT_ALIASES.put("ais", "AIS Statement (PDF)");
        DOCUMENT_ALIASES.put("ais statement", "AIS Statement (PDF)");
        DOCUMENT_ALIASES.put("annual information statement", "AIS Statement (PDF)");
        
        // TIS variations
        DOCUMENT_ALIASES.put("tis", "TIS Statement (PDF)");
        DOCUMENT_ALIASES.put("tis statement", "TIS Statement (PDF)");
        DOCUMENT_ALIASES.put("taxpayer information summary", "TIS Statement (PDF)");
        
        // Form 26AS variations
        DOCUMENT_ALIASES.put("form 26as", "Form 26AS");
        DOCUMENT_ALIASES.put("26as", "Form 26AS");

        // Bank statement variations
        DOCUMENT_ALIASES.put("bank statement", "Bank Statement (Last 1 Year)");
        DOCUMENT_ALIASES.put("bank statements", "Bank Statement (Last 1 Year)");
        DOCUMENT_ALIASES.put("bank statement (full year)", "Bank Statement (Full Year)");
        
        // Notice variations
        DOCUMENT_ALIASES.put("notice", "Notice PDF");
        DOCUMENT_ALIASES.put("tax notice", "Notice PDF");
        DOCUMENT_ALIASES.put("income tax notice", "Notice PDF");
        DOCUMENT_ALIASES.put("143(1) intimation", "Notice PDF");
        DOCUMENT_ALIASES.put("assessment order", "Assessment Order");
        
        // ITR variations
        DOCUMENT_ALIASES.put("itr", "Previous ITR");
        DOCUMENT_ALIASES.put("itr v", "Previous ITR");
        DOCUMENT_ALIASES.put("itr acknowledgement", "Previous ITR");
        DOCUMENT_ALIASES.put("previous itr", "Previous ITR");
        
        // GST variations
        DOCUMENT_ALIASES.put("gst certificate", "GST Login Credentials");
        DOCUMENT_ALIASES.put("gst login credentials", "GST Login Credentials");
        DOCUMENT_ALIASES.put("sales register", "Sales Register");
        DOCUMENT_ALIASES.put("purchase register", "Purchase Register");
        DOCUMENT_ALIASES.put("bank statement (last 6 months)", "Bank Statement (Last 1 Year)");
        DOCUMENT_ALIASES.put("bank statement (last 1 year)", "Bank Statement (Last 1 Year)");
        DOCUMENT_ALIASES.put("bank statement (full year)", "Bank Statement (Full Year)");
        DOCUMENT_ALIASES.put("bank account statement", "Bank Account Statement");
        DOCUMENT_ALIASES.put("passbook", "Bank Statement (Last 1 Year)");

        // Investment proofs
        DOCUMENT_ALIASES.put("investment proof", "Investment Proofs (80C/80D)");
        DOCUMENT_ALIASES.put("investment proofs", "Investment Proofs (80C/80D)");
        DOCUMENT_ALIASES.put("80c proof", "Investment Proofs (80C/80D)");
        DOCUMENT_ALIASES.put("80d proof", "Investment Proofs (80C/80D)");
        DOCUMENT_ALIASES.put("investment proofs (80c, 80d)", "Investment Proofs (80C/80D)");

        // Address proof
        DOCUMENT_ALIASES.put("address proof", "Business Address Proof");
        DOCUMENT_ALIASES.put("electricity bill", "Business Address Proof");
        DOCUMENT_ALIASES.put("rent agreement", "Business Address Proof");

        // Salary slips
        DOCUMENT_ALIASES.put("salary slip", "Salary Slips");
        DOCUMENT_ALIASES.put("salary slips", "Salary Slips");
        DOCUMENT_ALIASES.put("payslip", "Salary Slips");
    }

    public GeminiService(com.caCommand.caCommand.services.ai.AIProviderService aiProviderService, RestTemplate restTemplate, S3StorageService S3StorageService) {
        this.aiProviderService = aiProviderService;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.S3StorageService = S3StorageService;
    }

    // ============================================================
    // CACHE FOR API OPTIMIZATION
    // ============================================================
    private final Map<String, GeminiAIResponse> analyzeCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ============================================================
    // MAIN: Analyze user message — extract city, service, name, reply
    // ============================================================
    public GeminiAIResponse analyzeUserMessage(String userMessage, String currentCity,
                                                String currentService, String currentName) {
        String cityStr = isBlank(currentCity) ? "NOT_PROVIDED" : currentCity;
        String serviceStr = isBlank(currentService) ? "NOT_PROVIDED" : currentService;
        String nameStr = isBlank(currentName) ? "NOT_PROVIDED" : currentName;
        
        String cacheKey = (userMessage + "|" + cityStr + "|" + serviceStr + "|" + nameStr).toLowerCase();
        if (analyzeCache.containsKey(cacheKey)) {
            log.info("Serving analyzeUserMessage from cache to save API budget.");
            return analyzeCache.get(cacheKey);
        }

        String prompt = buildSmartPrompt(userMessage, cityStr, serviceStr, nameStr);

        try {
            Map<String, Object> requestBody = baseGeminiRequest(0.1, 1024, true);
            requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", buildSystemPrompt()))));
            requestBody.put("contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
            ));

            String aiText = executeGeminiRequestWithRetry(requestBody, TEXT_MODEL);
            GeminiAIResponse aiResponse = objectMapper.readValue(cleanJson(aiText), GeminiAIResponse.class);

            // Normalize service to exact key
            if (aiResponse.getService() != null) {
                String normalizedService = normalizeService(aiResponse.getService());
                aiResponse.setService(normalizedService);
            }

            // Attach required documents
            if (aiResponse.getService() != null && SERVICE_DOCUMENTS.containsKey(aiResponse.getService())) {
                aiResponse.setDocuments_required(SERVICE_DOCUMENTS.get(aiResponse.getService()));
            }

            analyzeCache.put(cacheKey, aiResponse);
            return aiResponse;
        } catch (Exception e) {
            log.warn("Gemini analyze call failed; using local fallback response. Error: {}", e.getMessage());
            return getIntelligentFallbackResponse(userMessage, cityStr, serviceStr, nameStr);
        }
    }

    // Backward compatible overload
    public GeminiAIResponse analyzeUserMessage(String userMessage, String currentCity, String currentService) {
        return analyzeUserMessage(userMessage, currentCity, currentService, null);
    }

    // ============================================================
    // Document verification with canonical name normalization
    // ============================================================
    public DocumentVerificationResult verifyCustomDocument(String documentUrl, String requestedDocumentName) {
        return verifyDocumentAgainstAllowedTypes(documentUrl, List.of(requestedDocumentName));
    }

    public DocumentVerificationResult verifyClientDocument(String documentUrl, String serviceType, boolean testMode) {
        List<String> allowedTypes = testMode
                ? List.of("PAN Card", "Aadhar Card")
                : getRequiredDocuments(serviceType);

        return verifyDocumentAgainstAllowedTypes(documentUrl, allowedTypes);
    }

    public List<String> getRequiredDocuments(String serviceType) {
        if (isBlank(serviceType)) {
            return List.of("PAN Card", "Aadhar Card");
        }
        return SERVICE_DOCUMENTS.getOrDefault(serviceType, List.of("PAN Card", "Aadhar Card"));
    }

    /**
     * Normalize a document type returned by AI to its canonical form.
     * E.g., "Aadhaar Card" → "Aadhar Card", "form 16" → "Form 16"
     */
    public String canonicalizeDocumentName(String rawName) {
        if (isBlank(rawName)) return rawName;
        String lower = rawName.trim().toLowerCase();
        return DOCUMENT_ALIASES.getOrDefault(lower, rawName.trim());
    }

    // ============================================================
    // PRIVATE: Document verification
    // ============================================================
    private DocumentVerificationResult verifyDocumentAgainstAllowedTypes(String imageUrl, List<String> allowedTypes) {
        if (isBlank(imageUrl) || allowedTypes == null || allowedTypes.isEmpty()) {
            return DocumentVerificationResult.invalid("Document URL or expected type is missing");
        }

        String allowedDocs = String.join(", ", allowedTypes);
        String verificationPrompt = String.format("""
                You are an expert Indian government document verification system with OCR capability.
                
                Task: Verify if the attached document image/PDF matches ONE of the required document types.
                
                Required document types accepted:
                %s
                
                Document identification rules:
                - PAN Card: Must show 10-character alphanumeric PAN number (format ABCDE1234F), "Permanent Account Number", Income Tax Department logo.
                - Aadhar Card / Aadhaar Card: Must show 12-digit Aadhaar number, UIDAI branding, or "Mera Aadhaar Meri Pehchaan".
                - Bank Statement: Must show bank letterhead, account number, transaction history table.
                - Form 16 / Form 16A: Must show TDS certificate structure, employer/employee details, tax computation.
                - Address Proof (Electricity Bill/Rent Agreement): Must show provider/owner name, address, billing period.
                - Investment Proofs: Must show 80C or 80D investment receipts, insurance premium receipts, or ELSS receipts.
                - GST credentials / return data: Must show GST number or return filing data.
                - Notice PDF: Must be an official Income Tax Department or GST notice.
                - Assessment Order: Must be an assessment order document from tax department.
                - Previous ITR: Must be an Income Tax Return Acknowledgement (ITR-V).
                - Photo: Must be a clear face photograph for official use.
                - AIS Statement (PDF): Must contain "Annual Information Statement", "Part A", "Part B", or Income Tax Department logo.
                - TIS Statement (PDF): Must contain "Taxpayer Information Summary (TIS)", or Income Tax Department logo.
                
                IMPORTANT:
                - Accept both front and back of Aadhar/PAN.
                - Accept scanned PDFs of these documents.
                - "Aadhaar" and "Aadhar" are the same document.
                - If document is unclear, blurry, or wrong type, mark INVALID.
                
                Return ONLY this strict JSON, no extra text:
                {
                  "verdict": "VALID or INVALID",
                  "document_type": "exact matched document name from the allowed list, or null",
                  "confidence": "HIGH or MEDIUM or LOW",
                  "reason": "1 sentence user-friendly explanation in simple English"
                }
                """, allowedDocs);

        try {
            boolean isPdf = imageUrl != null && imageUrl.toLowerCase().endsWith(".pdf");
            Map<String, Object> requestBody;
            
            if (isPdf) {
                PdfParseResult pdfResult = parsePdf(imageUrl, null);
                
                if (pdfResult.isPasswordProtected) {
                    return new DocumentVerificationResult(true, "AIS Statement (PDF)", "Password protected PDF accepted.");
                }
                
                if (!isBlank(pdfResult.text)) {
                    String extractedText = pdfResult.text;
                    // LLaMA 3.3 has 128k context, we can safely allow up to 300,000 chars for verification
                    if (extractedText.length() > 300000) {
                        extractedText = extractedText.substring(0, 300000);
                    }
                    String textPrompt = verificationPrompt + "\n\n--- DOCUMENT TEXT ---\n" + extractedText;
                    requestBody = baseGeminiRequest(0.0, 512, true);
                    requestBody.put("contents", List.of(
                            Map.of("parts", List.of(Map.of("text", textPrompt)))
                    ));
                } else if (pdfResult.base64Image != null) {
                    // Fallback to Vision Model using the rasterized Base64 image
                    requestBody = baseGeminiRequest(0.0, 512, true);
                    requestBody.put("contents", List.of(
                            Map.of(
                                    "parts", List.of(
                                            Map.of("text", verificationPrompt),
                                            Map.of("inline_data", Map.of(
                                                    "mime_type", "image/jpeg",
                                                    "data", pdfResult.base64Image
                                            ))
                                    )
                            )
                    ));
                } else {
                    return DocumentVerificationResult.invalid("PDF could not be read or is empty/scanned image without text.");
                }
            } else {
                String base64Image = downloadBase64(imageUrl);
                if (base64Image == null) {
                    return DocumentVerificationResult.invalid("Failed to download image for verification");
                }
                requestBody = baseGeminiRequest(0.0, 512, true);
                requestBody.put("contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", verificationPrompt),
                                        Map.of("inline_data", Map.of(
                                                "mime_type", "image/jpeg",
                                                "data", base64Image
                                        ))
                                )
                        )
                ));
            }

            String aiText = executeGeminiRequestWithRetry(requestBody, VISION_MODEL);
            JsonNode verdictNode = objectMapper.readTree(cleanJson(aiText));
            String verdict = verdictNode.path("verdict").asText();
            String documentType = verdictNode.path("document_type").asText(null);
            String reason = verdictNode.path("reason").asText("Unable to verify document clearly.");
            String confidence = verdictNode.path("confidence").asText("MEDIUM");

            log.info("Document verification: allowedTypes={} verdict={} docType={} confidence={}",
                    allowedDocs, verdict, documentType, confidence);

            if ("VALID".equalsIgnoreCase(verdict) && !isBlank(documentType)) {
                // Normalize to canonical name to prevent Aadhaar/Aadhar mismatch
                documentType = canonicalizeDocumentName(documentType);
                return new DocumentVerificationResult(true, documentType, reason);
            } else {
                return DocumentVerificationResult.invalid(reason != null ? reason : "Document did not match requested type.");
            }
        } catch (Exception e) {
            log.error("Document verification error", e);
            return DocumentVerificationResult.invalid("AI verification failed temporarily.");
        }
    }

    // ============================================================
    // Real Data Extraction from Uploaded Documents
    // ============================================================
    public Map<String, Object> extractFinancialData(String imageUrl, String documentType, String pdfPassword) {
        String extractionPrompt = String.format("""
                You are an expert Indian Chartered Accountant AI.
                Task: Extract exact financial figures from this uploaded document (%s).
                
                Rules:
                - If a value is not found, output 0.
                - Only extract exact numbers found in the document. Do not invent data.
                - Output ONLY valid JSON matching exactly this schema, with no additional text.
                
                {
                  "salaryIncome": 0,
                  "interestIncome": 0,
                  "dividendIncome": 0,
                  "capitalGains": 0,
                  "tds": 0,
                  "aisReportedIncome": 0,
                  "tisReportedIncome": 0,
                  "refundOpportunity": 0,
                  "demandOutstanding": 0,
                  "noticeType": "None",
                  "suggestedItr": "ITR-1",
                  "riskScore": "Low"
                }
                """, documentType);

        try {
            boolean isPdf = imageUrl != null && imageUrl.toLowerCase().endsWith(".pdf");
            Map<String, Object> requestBody;
            
            if (isPdf) {
                PdfParseResult pdfResult = parsePdf(imageUrl, pdfPassword);
                if (!isBlank(pdfResult.text)) {
                    String extractedText = pdfResult.text;
                    // Allow up to 400,000 characters for AIS/TIS extraction to ensure we don't cut off data
                    if (extractedText.length() > 400000) {
                        extractedText = extractedText.substring(0, 400000);
                        log.warn("Truncated PDF text from {} to 400000 characters to fit token limits", pdfResult.text.length());
                    }
                    String textPrompt = extractionPrompt + "\n\n--- DOCUMENT TEXT ---\n" + extractedText;
                    requestBody = baseGeminiRequest(0.0, 1024, true);
                    requestBody.put("contents", List.of(
                            Map.of("parts", List.of(Map.of("text", textPrompt)))
                    ));
                } else if (pdfResult.base64Image != null) {
                    // Fallback to Vision Model for scanned PDFs
                    requestBody = baseGeminiRequest(0.0, 1024, true);
                    requestBody.put("contents", List.of(
                            Map.of(
                                    "parts", List.of(
                                            Map.of("text", extractionPrompt),
                                            Map.of("inline_data", Map.of(
                                                    "mime_type", "image/jpeg",
                                                    "data", pdfResult.base64Image
                                            ))
                                    )
                            )
                    ));
                } else {
                    return new HashMap<>();
                }
            } else {
                String base64Image = downloadBase64(imageUrl);
                if (base64Image == null) return new HashMap<>();
                
                requestBody = baseGeminiRequest(0.0, 1024, true);
                requestBody.put("contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", extractionPrompt),
                                        Map.of("inline_data", Map.of(
                                                "mime_type", "image/jpeg",
                                                "data", base64Image
                                        ))
                                )
                        )
                ));
            }

            String aiText = executeGeminiRequestWithRetry(requestBody, VISION_MODEL);
            JsonNode node = objectMapper.readTree(cleanJson(aiText));
            
            Map<String, Object> result = new HashMap<>();
            result.put("salaryIncome", node.path("salaryIncome").asDouble(0));
            result.put("interestIncome", node.path("interestIncome").asDouble(0));
            result.put("dividendIncome", node.path("dividendIncome").asDouble(0));
            result.put("capitalGains", node.path("capitalGains").asDouble(0));
            result.put("tds", node.path("tds").asDouble(0));
            result.put("aisReportedIncome", node.path("aisReportedIncome").asDouble(0));
            result.put("tisReportedIncome", node.path("tisReportedIncome").asDouble(0));
            result.put("refundOpportunity", node.path("refundOpportunity").asDouble(0));
            result.put("demandOutstanding", node.path("demandOutstanding").asDouble(0));
            result.put("noticeType", node.path("noticeType").asText("None"));
            result.put("suggestedItr", node.path("suggestedItr").asText("ITR-1"));
            result.put("riskScore", node.path("riskScore").asText("Low"));
            
            return result;
        } catch (Exception e) {
            log.error("Data extraction error", e);
            return new HashMap<>(); // Empty map indicates failure
        }
    }

    // ============================================================
    // PRIVATE: Prompt builders
    // ============================================================
    private String buildSystemPrompt() {
        return """
                You are a smart, professional assistant for Phorwal CA Firm India.
                You help clients with CA services like ITR Filing, GST, Company Registration, etc.
                
                Rules:
                - Always return valid JSON only — no markdown, no extra text.
                - Extract information accurately from client messages.
                - Detect client name from phrases like "I am Rahul", "mera naam Priya hai", "this is Amit".
                - Detect city from message or context.
                - Map service requests to exact service names from the allowed list.
                - Reply messages must be in friendly Hinglish (mix of Hindi + English), warm and professional.
                - Keep WhatsApp replies concise — max 4-5 lines.
                - Never invent information not present in the message.
                """;
    }

    private String buildSmartPrompt(String userMessage, String cityStr, String serviceStr, String nameStr) {
        String availableServices = String.join(", ", SERVICE_DOCUMENTS.keySet());

        return String.format("""
                Current client context:
                - Name already known: "%s"
                - City already known: "%s"
                - Service already known: "%s"
                - Client's latest WhatsApp message: "%s"
                
                Your tasks:
                1. Extract client's FIRST NAME if mentioned (e.g., "I am Rahul", "mera naam Anjali hai", "Hi, Priya here").
                   Return null if name not found in message.
                2. Extract CITY name if mentioned. Return null if not found.
                3. Identify CA SERVICE from this exact list: %s
                   Return null if service not found. Match loosely (e.g., "tax filing" → "ITR Filing").
                4. Generate a friendly Hinglish WhatsApp reply based on what info is still missing.
                   - If name unknown → ask for name first.
                   - If city unknown → ask for city.
                   - If service unknown → ask which CA service they need.
                   - If all known → acknowledge and say documents will be requested next.
                5. Set conversation_stage appropriately.
                
                Return ONLY this JSON:
                {
                  "name": "extracted first name or null",
                  "city": "extracted city name or null",
                  "service": "matched service name or null",
                  "reply_message": "complete Hinglish WhatsApp reply (2-4 lines max)",
                  "documents_required": [],
                  "conversation_stage": "greeting | collecting_name | collecting_city | collecting_service | ready_for_documents"
                }
                """, nameStr, cityStr, serviceStr, userMessage, availableServices);
    }

    // ============================================================
    // PRIVATE: Fallback when API fails
    // ============================================================
    private GeminiAIResponse getIntelligentFallbackResponse(String userMessage, String cityStr,
                                                             String serviceStr, String nameStr) {
        String msgLower = userMessage == null ? "" : userMessage.toLowerCase().trim();
        GeminiAIResponse fallback = new GeminiAIResponse();

        fallback.setCity("NOT_PROVIDED".equals(cityStr) ? null : cityStr);
        fallback.setService("NOT_PROVIDED".equals(serviceStr) ? null : serviceStr);

        // Try to extract name from message
        String extractedName = extractNameFromMessage(msgLower, userMessage);
        fallback.setName(extractedName);

        // Try to detect service
        String detectedService = detectServiceFromMessage(msgLower);
        if (detectedService != null && fallback.getService() == null) {
            fallback.setService(detectedService);
        }

        // Build reply based on what's missing
        String name = "NOT_PROVIDED".equals(nameStr) ? extractedName : nameStr;
        String greeting = (name != null) ? "Namaste " + name + "! " : "Namaste! ";

        if (name == null && "NOT_PROVIDED".equals(nameStr)) {
            fallback.setReply_message(greeting + "Phorwal CA Firm mein aapka swagat hai!\n\nApna naam batao please, taki main aapki better help kar sakoon.");
            fallback.setConversation_stage("collecting_name");
        } else if (fallback.getCity() == null) {
            fallback.setReply_message(greeting + "Aap kaunse city se hain? City batao ya WhatsApp location share karo.");
            fallback.setConversation_stage("collecting_city");
        } else if (fallback.getService() == null) {
            fallback.setReply_message(greeting + "Kaunsi CA service chahiye?\n\nITR Filing, GST, Balance Sheet, Company Registration, PAN Card, TDS Return — koi bhi batao.");
            fallback.setConversation_stage("collecting_service");
            fallback.setDocuments_required(new ArrayList<>());
        } else {
            List<String> docs = SERVICE_DOCUMENTS.getOrDefault(fallback.getService(), Arrays.asList("PAN Card", "Aadhar Card"));
            fallback.setDocuments_required(docs);
            fallback.setConversation_stage("ready_for_documents");
            fallback.setReply_message(greeting + "Bilkul! " + fallback.getService() + " ke liye documents ki list bhej raha hoon. Please clear photos ya PDFs upload karein.");
        }

        return fallback;
    }

    // ============================================================
    // PRIVATE: Helpers
    // ============================================================
    private String normalizeService(String rawService) {
        if (isBlank(rawService)) return rawService;
        String lower = rawService.toLowerCase().trim();
        for (String key : SERVICE_DOCUMENTS.keySet()) {
            if (key.toLowerCase().equals(lower)) return key;
        }
        // Fuzzy match
        for (String key : SERVICE_DOCUMENTS.keySet()) {
            if (lower.contains(key.toLowerCase()) || key.toLowerCase().contains(lower)) return key;
        }
        return rawService; // Return as-is if no match
    }

    private String extractNameFromMessage(String msgLower, String originalMessage) {
        if (originalMessage == null) return null;

        // Patterns: "I am [Name]", "myself [Name]", "mera naam [Name] hai", "this is [Name]", "hi I am [Name]"
        String[] namePrefixes = {
                "i am ", "myself ", "my name is ", "mera naam ", "this is ", "hi i am ", "hii i am ",
                "m ", "main hoon ", "mai hoon ", "my self "
        };

        for (String prefix : namePrefixes) {
            int idx = msgLower.indexOf(prefix);
            if (idx >= 0) {
                String rest = originalMessage.substring(idx + prefix.length()).trim();
                // Take first word as name
                String[] words = rest.split("[\\s,\\.]+");
                if (words.length > 0 && words[0].length() >= 2) {
                    String name = words[0];
                    // Capitalize first letter
                    return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
                }
            }
        }
        return null;
    }

    private String detectServiceFromMessage(String message) {
        Map<String, String[]> serviceKeywords = new HashMap<>();
        serviceKeywords.put("ITR Filing", new String[]{"itr", "income tax", "tax return", "tax filing", "salary tax", "return file"});
        serviceKeywords.put("GST Registration", new String[]{"gst registration", "gst number", "new gst", "gst apply"});
        serviceKeywords.put("GST Return Filing", new String[]{"gst return", "gstr", "gst filing", "gst file karna"});
        serviceKeywords.put("Balance Sheet", new String[]{"balance sheet", "balance sheat"});
        serviceKeywords.put("Audit", new String[]{"audit", "financial audit"});
        serviceKeywords.put("Company Registration", new String[]{"company registration", "pvt ltd", "register company", "startup register"});
        serviceKeywords.put("PAN Card Application", new String[]{"pan card", "new pan", "pan apply", "pan banwana"});
        serviceKeywords.put("TDS Return Filing", new String[]{"tds return", "tds filing", "form 16", "tds file"});

        for (Map.Entry<String, String[]> entry : serviceKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (message.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Map<String, Object> baseGeminiRequest(double temperature, int maxOutputTokens, boolean isJson) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        // Force 8192 tokens to prevent unexpected EOF JSON truncation errors
        generationConfig.put("maxOutputTokens", 8192);
        if (isJson) {
            generationConfig.put("responseMimeType", "application/json");
        }
        requestBody.put("generationConfig", generationConfig);
        return requestBody;
    }

    private String executeGeminiRequestWithRetry(Map<String, Object> requestBody, String model) throws Exception {
        int maxRetries = 3;
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                return executeGeminiRequestInternal(requestBody, model);
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                attempt++;
                log.warn("Gemini API Error (5xx). Attempt {} of {}. Error: {}", attempt, maxRetries, e.getMessage());
                if (attempt >= maxRetries) {
                    throw e;
                }
                Thread.sleep(2000L * attempt); // exponential backoff
            }
        }
        throw new IllegalStateException("Gemini API failed after retries");
    }

        private String executeGeminiRequestInternal(Map<String, Object> requestBody, String model) throws Exception {
        try {
            List<Map<String, Object>> contents = (List<Map<String, Object>>) requestBody.get("contents");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.get(0).get("parts");
            String prompt = "";
            String base64Image = null;
            
            for (Map<String, Object> part : parts) {
                if (part.containsKey("text")) {
                    prompt = (String) part.get("text");
                }
                if (part.containsKey("inline_data")) {
                    Map<String, Object> inlineData = (Map<String, Object>) part.get("inline_data");
                    base64Image = (String) inlineData.get("data");
                }
            }
            
            if (base64Image != null) {
                return aiProviderService.generateTextFromImage(prompt, base64Image);
            } else {
                return aiProviderService.generateText(prompt);
            }
        } catch (Exception e) {
            log.error("Failed to proxy Gemini request to AIProviderService", e);
            throw new IllegalStateException("AI Provider failed", e);
        }
    }

    private String cleanJson(String value) {
        String cleaned = value.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String generateAisSummaryMessage(AisData ais, String clientName) {
        try {
            String prompt = "You are an expert Chartered Accountant AI assistant. "
                    + "Write a friendly, professional WhatsApp message summarizing this AIS data for " + clientName + ".\n\n"
                    + "AIS DATA:\n"
                    + "Salary: " + ais.getSalaryIncome() + "\n"
                    + "FD Interest: " + ais.getFdInterestIncome() + "\n"
                    + "Equity Gains: " + ais.getEquityCapitalGains() + "\n"
                    + "Business Income: " + ais.getBusinessIncome() + "\n"
                    + "Pending Demand: " + (ais.getHasPendingDemand() ? ais.getPendingDemandAmt() : "None") + "\n"
                    + "Suggested ITR Form: " + ais.getSuggestedItrForm() + "\n"
                    + "Approximate Fee: " + ais.getSuggestedFee() + "\n\n"
                    + "Format the message using WhatsApp markdown (*bold* etc) with emojis. "
                    + "Keep it concise but highlight key numbers. End by asking them to select a service from the menu.";

            Map<String, Object> requestBody = baseGeminiRequest(0.4, 300, false);
            requestBody.put("contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
            ));

            return executeGeminiRequestWithRetry(requestBody, TEXT_MODEL);
        } catch (Exception e) {
            log.error("Failed to generate AIS summary message: {}", e.getMessage());
            return "👨‍💼 *Aapka Income Tax Profile Ready Hai!*\n\n"
                    + "Aapka AIS data successfully fetch ho gaya hai. Aapke tax profile ke hisaab se aapko *"
                    + ais.getSuggestedItrForm() + "* file karni hogi.\n\n"
                    + "💵 *Approximate Fee: ₹" + ais.getSuggestedFee() + "*\n\n"
                    + "Kripya niche diye gaye menu se service select karein:";
        }
    }

    private String downloadBase64(String imageUrl) {
        try {
            String signedUrl = S3StorageService.getSignedUrl(imageUrl);
            URL url = new URL(signedUrl);
            try (InputStream in = url.openStream()) {
                byte[] bytes = in.readAllBytes();
                return Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception e) {
            log.error("Failed to download image", e);
            return null;
        }
    }

    private static class PdfParseResult {
        String text;
        String base64Image;
        boolean isPasswordProtected = false;
    }

    private PdfParseResult parsePdf(String pdfUrl, String password) {
        PdfParseResult result = new PdfParseResult();
        java.io.File tempFile = null;
        try {
            // S3Client download directly to a temp file, avoiding HTTP SocketTimeouts
            tempFile = S3StorageService.downloadMediaLocally(pdfUrl);
            
            PDDocument document = null;
            try {
                if (password != null && !password.isEmpty()) {
                    try {
                        document = PDDocument.load(tempFile, password); // Uses Random Access File buffering natively!
                    } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                        log.warn("Invalid password provided for PDF, attempting without password...");
                        try {
                            document = PDDocument.load(tempFile);
                        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
                            result.isPasswordProtected = true;
                            return result;
                        }
                    }
                } else {
                    try {
                        document = PDDocument.load(tempFile);
                    } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                        log.info("PDF is password protected. Marking as protected.");
                        result.isPasswordProtected = true;
                        return result;
                    }
                }
                
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                result.text = text != null ? text.trim() : "";
                
                // Fallback: If no text is found, rasterize the first page to image for Vision model
                if (result.text.isEmpty() && document.getNumberOfPages() > 0) {
                    org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                    java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 150, org.apache.pdfbox.rendering.ImageType.RGB);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(bim, "jpg", baos);
                    result.base64Image = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                }
            } finally {
                if (document != null) {
                    document.close();
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract text from PDF: " + pdfUrl, e);
            result.text = "";
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete(); // Free up disk space!
            }
        }
        return result;
    }
}
