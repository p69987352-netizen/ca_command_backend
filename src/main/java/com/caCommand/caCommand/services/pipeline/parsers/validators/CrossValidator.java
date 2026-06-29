package com.caCommand.caCommand.services.pipeline.parsers.validators;

import com.caCommand.caCommand.models.TaxProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-validates data from multiple documents (TIS vs 26AS).
 * Generates warnings for mismatches that CAs should review.
 */
@Slf4j
@Component
public class CrossValidator {

    /**
     * Cross-validate the merged TaxProfile for internal consistency.
     * Returns list of warning messages.
     */
    public List<String> validate(TaxProfile profile) {
        List<String> warnings = new ArrayList<>();

        // Rule 1: TDS should not exceed total income (suspicious)
        double totalIncome = profile.computeTotalGrossIncome();
        
        if (profile.getTaxes().getTotalTds() > totalIncome && totalIncome > 0) {
            warnings.add(String.format("⚠ TDS (₹%,.0f) exceeds Total Income (₹%,.0f). Verify extraction.",
                    profile.getTaxes().getTotalTds(), totalIncome));
        }

        // Rule 2: Negative values check
        if (profile.getIncome().getSalary() < 0) warnings.add("❌ Negative Salary detected. Likely parser error.");
        if (profile.getIncome().getTotalInterest() < 0) warnings.add("❌ Negative Interest detected. Likely parser error.");
        if (profile.getIncome().getDividend() < 0) warnings.add("❌ Negative Dividend detected. Likely parser error.");
        if (profile.getTaxes().getTotalTds() < 0) warnings.add("❌ Negative TDS detected. Likely parser error.");

        // Rule 3: Unusually high values
        if (profile.getIncome().getTotalCapitalGains() > 500000000) { // > 50 Cr
            warnings.add("⚠ Capital Gains > ₹50 Crore. Verify this is correct.");
        }
        if (profile.getIncome().getBusiness() > 500000000) {
            warnings.add("⚠ Business Income > ₹50 Crore. Verify this is correct.");
        }

        // Rule 4: PAN validation
        if (profile.getPersonalInfo().getPanNumber() == null || profile.getPersonalInfo().getPanNumber().isBlank()) {
            warnings.add("⚠ PAN not extracted from any document.");
        }

        // Rule 5: Missing essential fields
        if (profile.getPersonalInfo().getAssesseeName() == null || profile.getPersonalInfo().getAssesseeName().isBlank()) {
            warnings.add("⚠ Assessee Name not extracted.");
        }
        if (profile.getPersonalInfo().getFinancialYear() == null || profile.getPersonalInfo().getFinancialYear().isBlank()) {
            warnings.add("⚠ Financial Year not extracted.");
        }

        // Log results
        if (warnings.isEmpty()) {
            log.info("CrossValidator: ✅ All checks passed");
        } else {
            log.info("CrossValidator: {} warnings found", warnings.size());
            for (String w : warnings) {
                log.warn("  {}", w);
            }
        }

        return warnings;
    }
}
