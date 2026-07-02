package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.PinCodeTier;
import com.caCommand.caCommand.entities.ServicePricing;
import com.caCommand.caCommand.repositories.PinCodeTierRepository;
import com.caCommand.caCommand.repositories.ServicePricingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final ServicePricingRepository pricingRepository;
    private final PinCodeTierRepository pinCodeTierRepository;
    private final com.caCommand.caCommand.repositories.PricingRuleRepository pricingRuleRepository;

    public PricingService(ServicePricingRepository pricingRepository,
                          PinCodeTierRepository pinCodeTierRepository,
                          com.caCommand.caCommand.repositories.PricingRuleRepository pricingRuleRepository) {
        this.pricingRepository = pricingRepository;
        this.pinCodeTierRepository = pinCodeTierRepository;
        this.pricingRuleRepository = pricingRuleRepository;
    }

    /**
     * Get tier for a PIN code. Returns "A" if not found (default full price).
     */
    public String getTierForPinCode(String pinCode) {
        if (pinCode == null || pinCode.isBlank()) return "A";
        return pinCodeTierRepository.findByPinCode(pinCode.trim())
                .map(PinCodeTier::getTier)
                .orElse("A");
    }

    /**
     * Get discount % for a PIN code.
     */
    public int getDiscountForPinCode(String pinCode) {
        if (pinCode == null || pinCode.isBlank()) return 0;
        return pinCodeTierRepository.findByPinCode(pinCode.trim())
                .map(PinCodeTier::getDiscountPercent)
                .orElse(0);
    }

    /**
     * Calculate the suggested fee for a service based on PIN code + income range.
     * Returns: { cardRate, suggestedFee, discountPercent, tier, pinDiscount, incomeDiscount }
     */
    public PricingResult calculateFee(String serviceKey, String pinCode, String incomeRange) {
        Optional<ServicePricing> pricingOpt = pricingRepository.findByServiceKeyIgnoreCase(serviceKey);

        if (pricingOpt.isEmpty()) {
            // Try searching by display name loosely
            List<ServicePricing> all = pricingRepository.findByIsActiveTrue();
            pricingOpt = all.stream()
                    .filter(p -> p.getDisplayName().toLowerCase().contains(serviceKey.toLowerCase())
                            || (p.getItrFormType() != null && p.getItrFormType().equalsIgnoreCase(serviceKey)))
                    .findFirst();
        }

        if (pricingOpt.isEmpty()) {
            return new PricingResult(0, 0, 0, "A", 0, 0, "No pricing found for: " + serviceKey);
        }

        ServicePricing pricing = pricingOpt.get();
        double cardRate = pricing.getCardRate();
        String tier = getTierForPinCode(pinCode);
        int pinDiscount = getDiscountForPinCode(pinCode);

        // Base rate after PIN code discount
        double baseRate = switch (tier) {
            case "B" -> pricing.getTierBRate();
            case "C" -> pricing.getTierCRate();
            default -> cardRate; // Tier A = full card rate
        };

        // Additional income-based discount
        int incomeDiscount = 0;
        if ("BELOW_5L".equals(incomeRange) || "below_5l".equalsIgnoreCase(incomeRange)) {
            incomeDiscount = pricing.getIncomeBelowFiveLakhDiscount() != null
                    ? pricing.getIncomeBelowFiveLakhDiscount() : 5;
        }

        double finalRate = baseRate * (1 - (incomeDiscount / 100.0));
        int totalDiscount = pinDiscount + incomeDiscount;

        return new PricingResult(
                cardRate,
                Math.round(finalRate),
                totalDiscount,
                tier,
                pinDiscount,
                incomeDiscount,
                null
        );
    }

    /**
     * Get all active pricing entries.
     */
    public List<ServicePricing> getAllPricing() {
        return pricingRepository.findByIsActiveTrue();
    }

    /**
     * Suggest ITR form type based on client type and income.
     */
    public String suggestItrForm(String clientType, String incomeRange) {
        if (clientType == null) return "ITR-1";
        return switch (clientType.toUpperCase()) {
            case "SALARIED" -> "ITR-1"; // Simple salaried, one house
            case "BUSINESS" -> "ITR-3"; // Business income
            case "PROFESSIONAL" -> "ITR-4"; // Presumptive income (44ADA)
            case "INVESTOR" -> "ITR-2";   // Capital gains
            case "COMPANY" -> "ITR-6";
            case "LLP", "FIRM" -> "ITR-5";
            default -> "ITR-1";
        };
    }

    /**
     * Get PIN code info.
     */
    public Optional<PinCodeTier> getPinCodeInfo(String pinCode) {
        return pinCodeTierRepository.findByPinCode(pinCode);
    }

    /**
     * AI Dynamic Pricing Engine V2
     */
    public void applyClearTaxPricing(com.caCommand.caCommand.entities.Ticket ticket, com.caCommand.caCommand.entities.ExtractedData data) {
        String serviceType = ticket.getServiceType() != null ? ticket.getServiceType() : "ITR Filing";
        String normalizedServiceType = "ITR_BASIC";
        
        if (serviceType.contains("ITR")) {
            if (data != null) {
                if ((data.getDividendIncome() != null && data.getDividendIncome() > 50000) || 
                    (data.getTds() != null && data.getTds() > 50000)) {
                    normalizedServiceType = "ITR_PREMIUM";
                    ticket.setItrFormType("ITR-2 / ITR-3");
                } else if (data.getSalaryIncome() != null && data.getSalaryIncome() > 5000000) {
                    normalizedServiceType = "ITR_ELITE";
                    ticket.setItrFormType("ITR-2");
                } else {
                    normalizedServiceType = "ITR_BASIC";
                    ticket.setItrFormType("ITR-1");
                }
            } else {
                normalizedServiceType = "ITR_BASIC";
            }
        } else if (serviceType.contains("GST")) {
            normalizedServiceType = "GST";
        } else if (serviceType.contains("Audit") || serviceType.contains("Company")) {
            normalizedServiceType = "AUDIT";
        } else if (serviceType.contains("Notice") || serviceType.contains("Appeal")) {
            normalizedServiceType = "NOTICE";
        } else {
            normalizedServiceType = "OTHER";
        }

        // Fetch from DB or fallback
        com.caCommand.caCommand.entities.PricingRule rule = pricingRuleRepository.findByServiceTypeAndIsActiveTrue(normalizedServiceType)
            .orElseGet(() -> {
                com.caCommand.caCommand.entities.PricingRule defaultRule = new com.caCommand.caCommand.entities.PricingRule();
                defaultRule.setBaseFee(1499.0);
                return defaultRule;
            });

        double baseFee = rule.getBaseFee();
        double calculatedFee = baseFee;

        ticket.setCardRateFee(calculatedFee);
        ticket.setDiscountPercent(25);
        double finalFee = calculatedFee - (calculatedFee * 0.25);
        ticket.setQuotedFee(finalFee);
    }

    // ======================================================
    // Result Record
    // ======================================================
    public record PricingResult(
            double cardRate,
            double suggestedFee,
            int totalDiscountPercent,
            String tier,
            int pinDiscount,
            int incomeDiscount,
            String error
    ) {
        public boolean hasError() { return error != null && !error.isBlank(); }
        public Map<String, Object> toMap() {
            return Map.of(
                    "cardRate", cardRate,
                    "suggestedFee", suggestedFee,
                    "totalDiscountPercent", totalDiscountPercent,
                    "tier", tier,
                    "pinDiscount", pinDiscount,
                    "incomeDiscount", incomeDiscount,
                    "savings", Math.round(cardRate - suggestedFee)
            );
        }
    }
}
