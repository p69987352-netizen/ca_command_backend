package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.services.ai.AIProviderService;
import com.caCommand.caCommand.services.config.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentClassifierService {

    private final FeatureFlagService featureFlagService;
    private final AIProviderService aiProviderService;

    public ClassificationResult classifyDocument(String ocrText, String expectedType) {
        // Attempt local rule engine classification first
        if (featureFlagService.isEnabled("RULE_ENGINE_LOCAL")) {
            ClassificationResult localResult = classifyLocally(ocrText, expectedType);
            if (localResult.getConfidence() >= 95) {
                log.info("Local Classification Success: {} with {}% confidence", expectedType, localResult.getConfidence());
                return localResult;
            } else if (localResult.getConfidence() >= 80) {
                log.info("Local Classification Hybrid: {} with {}% confidence. Needs AI verification.", expectedType, localResult.getConfidence());
                // Fallback to AI
                return classifyViaAI(ocrText, expectedType);
            }
        }
        
        // Fallback to AI entirely if confidence < 80% or local rule engine is off
        log.info("Local Classification failed/skipped. Falling back to AI verification.");
        return classifyViaAI(ocrText, expectedType);
    }

    private ClassificationResult classifyLocally(String text, String expectedType) {
        String normalizedText = text.toUpperCase().replaceAll("\\s+", " ");
        int confidence = 0;
        boolean isValid = false;
        String reason = "";

        switch (expectedType.toUpperCase()) {
            case "PAN CARD":
                if (normalizedText.contains("INCOME TAX DEPARTMENT") && normalizedText.contains("GOVT. OF INDIA")) {
                    confidence += 50;
                }
                if (Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]{1}").matcher(normalizedText).find()) {
                    confidence += 50;
                }
                isValid = confidence >= 80;
                reason = isValid ? "Matched PAN Card patterns locally" : "Could not find valid PAN regex";
                break;
            case "AADHAR CARD":
            case "AADHAAR CARD":
                if (normalizedText.contains("GOVERNMENT OF INDIA") || normalizedText.contains("AADHAAR")) {
                    confidence += 50;
                }
                if (Pattern.compile("\\d{4}\\s?\\d{4}\\s?\\d{4}").matcher(normalizedText).find()) {
                    confidence += 50;
                }
                isValid = confidence >= 80;
                reason = isValid ? "Matched Aadhaar patterns locally" : "Could not find valid Aadhaar regex";
                break;
            case "AIS":
            case "ANNUAL INFORMATION STATEMENT":
                if (normalizedText.contains("ANNUAL INFORMATION STATEMENT") || normalizedText.contains("PART B")) {
                    confidence = 100;
                    isValid = true;
                    reason = "Exact keyword match for AIS";
                }
                break;
            case "TIS":
            case "TAXPAYER INFORMATION SUMMARY":
                if (normalizedText.contains("TAXPAYER INFORMATION SUMMARY")) {
                    confidence = 100;
                    isValid = true;
                    reason = "Exact keyword match for TIS";
                }
                break;
            default:
                confidence = 0;
                reason = "No local rule exists for " + expectedType;
        }

        return new ClassificationResult(isValid, reason, confidence);
    }

    private ClassificationResult classifyViaAI(String text, String expectedType) {
        // Only send first 5000 and last 5000 chars to save tokens and prevent 503 errors
        String compressedText = text;
        if (text.length() > 10000) {
            compressedText = text.substring(0, 5000) + "\n...[TRUNCATED]...\n" + text.substring(text.length() - 5000);
        }

        String prompt = String.format("""
                You are a Document Verifier.
                Does the following text appear to be a valid '%s'?
                Reply only with 'YES' or 'NO'.
                
                --- DOCUMENT TEXT ---
                %s
                """, expectedType, compressedText);

        try {
            String response = aiProviderService.generateText(prompt);
            boolean isValid = response.trim().toUpperCase().contains("YES");
            return new ClassificationResult(isValid, isValid ? "AI Verified" : "AI Rejected", isValid ? 90 : 0);
        } catch (Exception e) {
            log.error("AI Classification failed", e);
            return new ClassificationResult(false, "AI Verification Failed due to " + e.getMessage(), 0);
        }
    }

    public static class ClassificationResult {
        private final boolean valid;
        private final String reason;
        private final int confidence;

        public ClassificationResult(boolean valid, String reason, int confidence) {
            this.valid = valid;
            this.reason = reason;
            this.confidence = confidence;
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public int getConfidence() { return confidence; }
    }
}
