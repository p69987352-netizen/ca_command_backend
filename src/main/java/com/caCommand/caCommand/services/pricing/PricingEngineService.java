package com.caCommand.caCommand.services.pricing;

import com.caCommand.caCommand.models.TaxProfile;
import com.caCommand.caCommand.services.pipeline.engines.ComplexityEngine;
import com.caCommand.caCommand.services.pipeline.engines.ComplexityEngine.ComplexityResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class PricingEngineService {

    private final ComplexityEngine complexityEngine;
    private final PricingConfig pricingConfig;

    public PricingEngineService(ComplexityEngine complexityEngine, PricingConfig pricingConfig) {
        this.complexityEngine = complexityEngine;
        this.pricingConfig = pricingConfig;
    }

    /**
     * New pricing: TaxProfile → ComplexityEngine → Pricing
     * Pipeline should call this method.
     */
    public PricingResult calculateFromProfile(TaxProfile profile, String itrForm) {
        // Step 1: Get complexity score
        ComplexityResult complexity = complexityEngine.evaluate(profile, itrForm);
        
        // Step 2: Get base price for ITR form from config
        double basePrice = pricingConfig.getBasePrices()
                .getOrDefault(itrForm, pricingConfig.getBasePrices().getOrDefault("DEFAULT", 2449.0));
        
        // Step 3: Remove complexity multiplier as requested by user
        double multiplier = 1.0;
        double adjustedPrice = basePrice * multiplier;
        
        // Step 4: Remove income surcharge as requested by user
        double totalIncome = profile.computeTotalGrossIncome();
        double surcharge = 0;
        
        // Step 4.5: Capital Gains lines surcharge (> 10 lines, +500 for every 10)
        int cgLines = profile.getIncome().getCapitalGainsLineCount();
        double cgSurcharge = 0; // USER REQUEST: strict base price only
        
        double finalPrice = adjustedPrice + surcharge + cgSurcharge;
        
        // Step 5: Apply min/max caps
        finalPrice = Math.max(finalPrice, pricingConfig.getMinFee());
        finalPrice = Math.min(finalPrice, pricingConfig.getMaxFee());
        
        // Round to nearest integer
        finalPrice = Math.round(finalPrice);
        
        log.info("Pricing: base={}, multiplier={}x ({}), incomeSurcharge={}, cgSurcharge={}, final=INR {}",
                basePrice, multiplier, complexity.getLevel(), surcharge, cgSurcharge, finalPrice);
        
        return new PricingResult(finalPrice, complexity, itrForm);
    }

    /**
     * Legacy compatibility: Map<String, Object> → double price
     * Existing pipeline code calls this. We bridge to new system.
     */
    public double calculatePrice(Map<String, Object> structuredJson) {
        // Build a minimal TaxProfile from legacy map
        TaxProfile profile = new TaxProfile();
        profile.getIncome().setSalary(getDouble(structuredJson, "salaryIncome"));
        profile.getIncome().setInterestOther(getDouble(structuredJson, "interestIncome"));
        profile.getIncome().setDividend(getDouble(structuredJson, "dividendIncome"));
        profile.getIncome().setCapitalGainsStcg(getDouble(structuredJson, "capitalGains"));
        profile.getIncome().setBusiness(getDouble(structuredJson, "businessIncome"));
        profile.getTaxes().setTotalTds(getDouble(structuredJson, "tds"));
        profile.getIncome().setRent(getDouble(structuredJson, "rentIncome"));
        
        // Quick ITR recommendation
        String itrForm = "ITR-1";
        if (profile.getIncome().getBusiness() > 0) itrForm = "ITR-3";
        else if (profile.getIncome().getTotalCapitalGains() > 0 || profile.computeTotalGrossIncome() > 5000000) itrForm = "ITR-2";
        
        return calculateFromProfile(profile, itrForm).getFinalPrice();
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble(((String) val).replaceAll("[^\\d.]", "")); }
            catch (Exception e) { return 0.0; }
        }
        return 0.0;
    }

    // --- Result Record ---
    @Data
    public static class PricingResult {
        private final double finalPrice;
        private final ComplexityResult complexity;
        private final String itrForm;
    }

    // --- Configuration Bean ---
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "pricing")
    public static class PricingConfig {
        private Map<String, Double> basePrices = Map.of(
            "ITR-1", 2449.0, "ITR-2", 4449.0, "ITR-3", 6499.0, "ITR-4", 4449.0, "DEFAULT", 2449.0
        );
        private Map<String, Double> complexityMultipliers = Map.of(
            "SIMPLE", 1.0, "MODERATE", 1.25, "COMPLEX", 1.60
        );
        private Map<String, Double> incomeSurcharge = Map.of(
            "above-1cr", 3000.0, "above-50l", 1500.0, "above-20l", 500.0, "default", 0.0
        );
        private Map<String, Double> capitalGainsLinesSurcharge = Map.of(
            "per-10-lines", 500.0
        );
        private double minFee = 1499;
        private double maxFee = 25000;
    }
}
