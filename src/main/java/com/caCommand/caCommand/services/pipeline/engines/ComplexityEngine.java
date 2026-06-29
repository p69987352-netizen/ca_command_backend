package com.caCommand.caCommand.services.pipeline.engines;

import com.caCommand.caCommand.models.TaxProfile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates filing complexity score based on TaxProfile.
 * Pricing depends on complexity, NOT raw income values.
 * 
 * Complexity Score: 1-10
 * 1-3: Simple (salary only, single source)
 * 4-6: Moderate (multiple sources, some deductions)
 * 7-10: Complex (capital gains, business income, foreign income, notices)
 */
@Slf4j
@Service
public class ComplexityEngine {

    @Data
    @AllArgsConstructor
    public static class ComplexityResult {
        private int score;          // 1-10
        private String level;       // SIMPLE, MODERATE, COMPLEX
        private String itrForm;     // ITR-1, ITR-2, etc.
        private List<String> factors;  // What contributed to complexity
        
        public static ComplexityResult simple(String itrForm, List<String> factors) {
            return new ComplexityResult(Math.min(factors.size() + 1, 3), "SIMPLE", itrForm, factors);
        }
        
        public static ComplexityResult moderate(int score, String itrForm, List<String> factors) {
            return new ComplexityResult(Math.min(Math.max(score, 4), 6), "MODERATE", itrForm, factors);
        }
        
        public static ComplexityResult complex(int score, String itrForm, List<String> factors) {
            return new ComplexityResult(Math.min(Math.max(score, 7), 10), "COMPLEX", itrForm, factors);
        }
    }

    public ComplexityResult evaluate(TaxProfile profile, String itrForm) {
        List<String> factors = new ArrayList<>();
        int score = 1; // Base complexity
        
        // Factor 1: Number of income sources
        int incomeSources = 0;
        if (profile.getIncome().getSalary() > 0) { incomeSources++; factors.add("Salary Income"); }
        if (profile.getIncome().getTotalInterest() > 0) { incomeSources++; factors.add("Interest Income"); }
        if (profile.getIncome().getDividend() > 0) { incomeSources++; factors.add("Dividend Income"); }
        if (profile.getIncome().getRent() > 0) { incomeSources++; factors.add("Rental Income"); }
        if (profile.getIncome().getTotalCapitalGains() > 0) { incomeSources++; factors.add("Capital Gains"); score += 2; }
        if (profile.getIncome().getBusiness() > 0) { incomeSources++; factors.add("Business Income"); score += 3; }
        if (profile.getIncome().getAgriculture() > 0) { incomeSources++; factors.add("Agricultural Income"); score += 1; }
        if (profile.getIncome().getOther() > 0) { incomeSources++; factors.add("Other Income"); }
        
        // Multiple sources add complexity
        if (incomeSources >= 3) score += 1;
        if (incomeSources >= 5) score += 1;
        
        // Factor 2: Income level
        double totalIncome = profile.computeTotalGrossIncome();
        if (totalIncome > 10000000) { score += 2; factors.add("Income > 1 Cr"); }
        else if (totalIncome > 5000000) { score += 1; factors.add("Income > 50L"); }
        
        // Factor 3: ITR form complexity
        switch (itrForm) {
            case "ITR-1" -> { /* base, no addition */ }
            case "ITR-2" -> score += 1;
            case "ITR-3" -> score += 2;
            case "ITR-4" -> score += 1;
        }
        
        // Factor 4: Outstanding demand or refund
        if (profile.getTaxes().getOutstandingDemand() > 0) { score += 2; factors.add("Outstanding Demand"); }
        if (profile.getTaxes().getRefund() > 100000) { score += 1; factors.add("Refund Claim"); }
        
        // Cap score at 10
        score = Math.min(score, 10);
        
        // Determine level
        ComplexityResult result;
        if (score <= 3) {
            result = ComplexityResult.simple(itrForm, factors);
        } else if (score <= 6) {
            result = ComplexityResult.moderate(score, itrForm, factors);
        } else {
            result = ComplexityResult.complex(score, itrForm, factors);
        }
        
        log.info("Complexity Analysis: score={}/10, level={}, itr={}, factors={}",
                result.getScore(), result.getLevel(), itrForm, factors);
        
        return result;
    }
}
