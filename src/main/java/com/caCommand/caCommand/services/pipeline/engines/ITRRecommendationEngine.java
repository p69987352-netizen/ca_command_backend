package com.caCommand.caCommand.services.pipeline.engines;

import com.caCommand.caCommand.models.TaxProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Recommends the appropriate ITR form based on TaxProfile data.
 * 
 * ITR Form Rules (AY 2026-27):
 * - ITR-1 (Sahaj): Resident, income ≤ 50L, salary/pension + 1 house property + other sources
 * - ITR-2: No business income, has capital gains OR income > 50L OR multiple properties
 * - ITR-3: Has business/professional income
 * - ITR-4 (Sugam): Presumptive income under 44AD/44ADA, income ≤ 50L
 */
@Slf4j
@Service
public class ITRRecommendationEngine {

    public String recommend(TaxProfile profile) {
        double totalIncome = profile.computeTotalGrossIncome();
        
        // Rule 1: Business/Professional Income → ITR-3
        if (profile.getIncome().getBusiness() > 0) {
            // Check if eligible for presumptive (ITR-4)
            if (totalIncome <= 5000000 && profile.getIncome().getTotalCapitalGains() == 0) {
                log.info("ITR Recommendation: ITR-4 (Presumptive Business, income ≤ 50L)");
                return "ITR-4";
            }
            log.info("ITR Recommendation: ITR-3 (Business/Professional Income)");
            return "ITR-3";
        }
        
        // Rule 2: Capital Gains → ITR-2
        if (profile.getIncome().getTotalCapitalGains() > 0) {
            log.info("ITR Recommendation: ITR-2 (Capital Gains present)");
            return "ITR-2";
        }
        
        // Rule 3: Income > 50 Lakh → ITR-2
        if (totalIncome > 5000000) {
            log.info("ITR Recommendation: ITR-2 (Total income > 50L)");
            return "ITR-2";
        }
        
        // Rule 4: Multiple house properties or foreign income → ITR-2
        // (Can be enhanced later with more data)
        
        // Rule 5: Agricultural income > 5000 → ITR-2
        if (profile.getIncome().getAgriculture() > 5000) {
            log.info("ITR Recommendation: ITR-2 (Agricultural income > 5000)");
            return "ITR-2";
        }
        
        // Default: Simple salary/pension + interest + dividend ≤ 50L → ITR-1
        log.info("ITR Recommendation: ITR-1 (Simple salary income ≤ 50L)");
        return "ITR-1";
    }
}
