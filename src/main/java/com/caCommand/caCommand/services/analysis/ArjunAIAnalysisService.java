package com.caCommand.caCommand.services.analysis;

import com.caCommand.caCommand.services.ai.AIProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArjunAIAnalysisService {

    private final AIProviderService aiProviderService;

    public String generateRiskAnalysis(Map<String, Object> structuredJson) {
        String prompt = String.format("""
                You are Arjun AI, an expert Indian Chartered Accountant.
                Analyze the following structured financial data and provide a concise risk analysis, 
                readiness score, and any missing data warnings.
                Do not extract numbers, just reason about the provided numbers.
                
                --- DATA ---
                %s
                """, structuredJson.toString());

        return executeWithRetry(prompt, 3);
    }

    private String executeWithRetry(String prompt, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                attempts++;
                return aiProviderService.generateText(prompt);
            } catch (Exception e) {
                log.warn("AI Analysis Error (Attempt {}/{}): {}", attempts, maxRetries, e.getMessage());
                if (attempts >= maxRetries) {
                    log.error("AI Analysis failed completely after {} attempts. Falling back to manual review.", maxRetries);
                    return "ERROR: AI Analysis failed. Requires Manual Review.";
                }
                try {
                    Thread.sleep(2000 * attempts); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during AI retry", ie);
                }
            }
        }
        return "ERROR";
    }
}
