package com.caCommand.caCommand.services.pipeline.parsers.validators;

import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;

/**
 * Amount Validator v2.0 - Business rule validations per field.
 */
public class AmountValidator {

    public static void validateAmount(ExtractionResult result, String fieldKey, Double amount) {
        if (amount == null) return;

        // Rule 1: No negative income
        if (amount < 0) {
            result.addWarning("❌ " + fieldKey + " is negative (" + String.format("%,.0f", amount) + "). Likely parser error.");
        }

        // Rule 2: Unusually high single field (> 10 Crore)
        if (amount > 100000000) {
            result.addWarning("⚠ " + fieldKey + " is very high (" + String.format("%,.0f", amount) + "). Verify extraction.");
        }

        // Rule 3: TDS specific checks
        if ("tds".equals(fieldKey) || "tdsDeducted".equals(fieldKey)) {
            Double salary = getAmount(result, "salaryIncome");
            Double interest = getAmount(result, "interestIncome");
            double totalKnownIncome = (salary != null ? salary : 0) + (interest != null ? interest : 0);
            
            if (totalKnownIncome > 0 && amount > totalKnownIncome * 2) {
                result.addWarning("⚠ TDS (" + String.format("%,.0f", amount) 
                        + ") seems disproportionately high compared to known income (" 
                        + String.format("%,.0f", totalKnownIncome) + ").");
            }
        }

        // Rule 4: Interest > 1 Cr is suspicious for individual
        if ("interestIncome".equals(fieldKey) && amount > 10000000) {
            result.addWarning("⚠ Interest Income > ₹1 Crore. Possible duplicate entries. Verify.");
        }
    }

    private static Double getAmount(ExtractionResult result, String fieldKey) {
        if (result.getFields().containsKey(fieldKey)) {
            Object val = result.getFields().get(fieldKey).getValue();
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
            if (val instanceof String) {
                try { return Double.parseDouble(((String) val).replaceAll("[^\\d.]", "")); }
                catch (Exception e) { return null; }
            }
        }
        return null;
    }
}
