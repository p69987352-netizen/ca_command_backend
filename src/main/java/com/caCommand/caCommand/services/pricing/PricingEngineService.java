package com.caCommand.caCommand.services.pricing;

import com.caCommand.caCommand.dto.PricingAnalysisDto;
import com.caCommand.caCommand.entities.ComplexityRule;
import com.caCommand.caCommand.entities.PricingRule;
import com.caCommand.caCommand.models.TaxProfile;
import com.caCommand.caCommand.repositories.ComplexityRuleRepository;
import com.caCommand.caCommand.repositories.PricingRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PricingEngineService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ComplexityRuleRepository complexityRuleRepository;

    public PricingEngineService(PricingRuleRepository pricingRuleRepository, ComplexityRuleRepository complexityRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.complexityRuleRepository = complexityRuleRepository;
    }

    public PricingAnalysisDto calculateFromProfile(TaxProfile profile, String itrForm) {
        PricingAnalysisDto dto = new PricingAnalysisDto();
        dto.setVersion(1);
        dto.setConfidence(98);

        String serviceType = itrForm + " Filing";
        Optional<PricingRule> ruleOpt = pricingRuleRepository.findByServiceTypeAndIsActiveTrue(serviceType);
        
        PricingRule rule;
        if (ruleOpt.isPresent()) {
            rule = ruleOpt.get();
        } else {
            rule = new PricingRule();
            rule.setBaseFee(1500.0);
            rule.setMinFee(999.0);
            rule.setMaxFee(5000.0);
            rule.setDefaultDiscount(20.0);
        }

        dto.setCompetitorBenchmark(rule.getMaxFee() != null ? rule.getMaxFee() : rule.getBaseFee() * 1.5);
        dto.setStandardDiscount(rule.getDefaultDiscount() != null ? (dto.getCompetitorBenchmark() * rule.getDefaultDiscount() / 100) : 0);
        dto.setBaseFee(rule.getBaseFee());
        
        double currentFee = rule.getBaseFee();

        List<ComplexityRule> cRules = complexityRuleRepository.findByIsEnabledTrueOrderByPriorityDesc();
        
        for (ComplexityRule cr : cRules) {
            boolean apply = false;
            switch (cr.getCode()) {
                case "CAPITAL_GAIN":
                    if (profile.getIncome().getTotalCapitalGains() > 0) apply = true;
                    break;
                case "BUSINESS":
                    if (profile.getIncome().getBusiness() > 0 || profile.getIncome().getGstTurnover() > 0) apply = true;
                    break;
                case "INTEREST":
                    if (profile.getIncome().getInterestFd() > 0 || profile.getIncome().getDividend() > 0) apply = true;
                    break;
                case "FOREIGN_INCOME":
                    break;
            }

            if (apply) {
                dto.getComplexityAdjustments().add(new PricingAnalysisDto.ComplexityAdjustment(cr.getDisplayName(), cr.getAmount()));
                currentFee += cr.getAmount();
            }
        }

        if (rule.getMinFee() != null && currentFee < rule.getMinFee()) currentFee = rule.getMinFee();
        if (rule.getMaxFee() != null && currentFee > rule.getMaxFee()) currentFee = rule.getMaxFee();

        dto.setRecommendedFee(currentFee);
        
        return dto;
    }

    public double calculatePrice(java.util.Map<String, Object> structuredJson) {
        return 1500.0;
    }
}
