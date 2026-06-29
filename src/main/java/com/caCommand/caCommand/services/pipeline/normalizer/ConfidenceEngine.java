package com.caCommand.caCommand.services.pipeline.normalizer;

import com.caCommand.caCommand.models.TaxProfile;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceEngine {

    /**
     * Calculate confidence using a weighted scoring system.
     * PAN (20), FY (10), Assessee Name (10), Rows Extracted (30), Cross Validation (30)
     */
    public int calculateConfidence(TaxProfile profile) {
        int score = 0;
        
        // Identity (40 points)
        if (profile.getPersonalInfo().getPanNumber() != null && !profile.getPersonalInfo().getPanNumber().isBlank()) {
            score += 20;
        }
        if (profile.getPersonalInfo().getFinancialYear() != null && !profile.getPersonalInfo().getFinancialYear().isBlank()) {
            score += 10;
        }
        if (profile.getPersonalInfo().getAssesseeName() != null && !profile.getPersonalInfo().getAssesseeName().isBlank()) {
            score += 10;
        }
        
        // Extraction richness (30 points)
        int extractionScore = 0;
        if (profile.getDocuments().getDeductorCount() > 0) extractionScore += 15;
        if (profile.getDocuments().isTisParsed()) extractionScore += 15;
        // Fallback: if it's a single document type without rows but has income/TDS
        if (extractionScore == 0 && profile.computeTotalGrossIncome() > 0) extractionScore += 15;
        if (extractionScore == 0 && profile.getTaxes().getTotalTds() > 0) extractionScore += 15;
        
        score += Math.min(30, extractionScore);
        
        // Cross Validation (30 points)
        int warnings = profile.getValidation().getWarnings().size();
        if (warnings == 0) {
            score += 30;
        } else if (warnings == 1) {
            score += 20;
        } else if (warnings == 2) {
            score += 10;
        }
        
        return Math.min(100, score);
    }
}
