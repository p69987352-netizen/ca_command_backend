package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.PricingRule;
import com.caCommand.caCommand.repositories.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyPricingEngineService {

    private final PricingRuleRepository pricingRuleRepository;

    public double calculateFee(String serviceCategory, int aiComplexityScore) {
        
        Optional<PricingRule> ruleOpt = pricingRuleRepository.findByServiceTypeAndIsActiveTrue(serviceCategory);
        
        // Default base price if no rule found
        double basePrice = 1499.0;
        
        if (ruleOpt.isPresent()) {
            basePrice = ruleOpt.get().getBaseFee();
        }

        double finalFee = basePrice;

        // "Simple Case (0-30 Score) -> Base Price"
        // "Medium Case (31-70 Score) -> Base Price × 1.5"
        // "Complex Case (71-100 Score) -> Base Price × 2"
        if (aiComplexityScore > 70) {
            finalFee = basePrice * 2.0;
        } else if (aiComplexityScore > 30) {
            finalFee = basePrice * 1.5;
        }

        return Math.round(finalFee);
    }
}
