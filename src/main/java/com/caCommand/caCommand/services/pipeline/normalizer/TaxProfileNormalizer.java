package com.caCommand.caCommand.services.pipeline.normalizer;

import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.models.TaxProfile;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractedField;
import com.caCommand.caCommand.services.pipeline.parsers.models.DeductorEntry;
import com.caCommand.caCommand.services.pipeline.parsers.models.SummaryTableRow;
import com.caCommand.caCommand.services.pipeline.parsers.dictionary.KeywordDictionary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;

@Slf4j
@Service
public class TaxProfileNormalizer {

    public TaxProfile normalize(ExtractionResult extraction, DocumentType sourceType) {
        TaxProfile profile = new TaxProfile();
        
        Map<String, ExtractedField> fields = extraction.getFields();
        
        // 1. Identity fields
        profile.getPersonalInfo().setPanNumber(getStringValue(fields, "panNumber"));
        profile.getPersonalInfo().setAssesseeName(getStringValue(fields, "assesseeName", "taxpayerName", "name"));
        profile.getPersonalInfo().setFinancialYear(getStringValue(fields, "financialYear", "fy"));
        profile.getPersonalInfo().setAssessmentYear(getStringValue(fields, "assessmentYear", "ay"));
        
        // 2. Aggregate from Raw Lists (TIS Rows)
        for (SummaryTableRow row : extraction.getTisRows()) {
            String category = row.getCategory().toLowerCase().trim();
            double value = row.getBestValue();
            if (value <= 0) continue;
            
            if (KeywordDictionary.matchesCategory(category, KeywordDictionary.SALARY_ALIASES)) {
                profile.getIncome().setSalary(profile.getIncome().getSalary() + value);
            } else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.INTEREST_ALIASES)) {
                if (category.contains("saving")) {
                    profile.getIncome().setInterestSavings(profile.getIncome().getInterestSavings() + value);
                } else if (category.contains("deposit")) {
                    profile.getIncome().setInterestFd(profile.getIncome().getInterestFd() + value);
                } else {
                    profile.getIncome().setInterestOther(profile.getIncome().getInterestOther() + value);
                }
            } else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.DIVIDEND_ALIASES)) {
                profile.getIncome().setDividend(profile.getIncome().getDividend() + value);
            } else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.RENT_ALIASES)) {
                profile.getIncome().setRent(profile.getIncome().getRent() + value);
            } else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.CAPITAL_GAIN_ALIASES)) {
                profile.getIncome().setCapitalGainsStcg(profile.getIncome().getCapitalGainsStcg() + value); // Rough mapping
            } else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.BUSINESS_ALIASES)) {
                if (category.contains("gst")) {
                    profile.getIncome().setGstTurnover(profile.getIncome().getGstTurnover() + value);
                } else {
                    profile.getIncome().setBusiness(profile.getIncome().getBusiness() + value);
                }
            } else {
                profile.getIncome().setOther(profile.getIncome().getOther() + value);
            }
        }
        
        // 3. Aggregate from Raw Lists (26AS Deductors)
        if (extraction.getDeductors() != null) {
            profile.setDeductors(extraction.getDeductors());
            for (DeductorEntry d : extraction.getDeductors()) {
                profile.getTaxes().addTds(d.getSection(), d.getTdsDeducted());
            }
        }
        
        if (extraction.getTisRows() != null) {
            profile.setTisRows(extraction.getTisRows());
        }
        
        // 4. Flat Fields Fallback (for older parsers or missing raw data)
        if (profile.getTaxes().getTotalTds() == 0) {
            profile.getTaxes().setTotalTds(getAmount(fields, "tds", "tdsDeducted", "totalTds"));
        }
        profile.getTaxes().setAdvanceTax(getAmount(fields, "advanceTaxPaid", "advanceTax"));
        profile.getTaxes().setSelfAssessmentTax(getAmount(fields, "selfAssessmentTax", "selfAssessmentTaxPaid", "sat"));
        profile.getTaxes().setRefund(getAmount(fields, "refundAmount", "refund", "refundDue"));
        
        // Metadata
        profile.getDocuments().getSourcesUsed().add(sourceType.name());
        profile.getDocuments().setTisParsed(!extraction.getTisRows().isEmpty());
        profile.getDocuments().setForm26asParsed(!extraction.getDeductors().isEmpty());
        profile.getDocuments().setDeductorCount(extraction.getDeductors().size());
        profile.getValidation().setConfidenceScore(extraction.getOverallConfidence());
        profile.getValidation().getWarnings().addAll(extraction.getWarnings());
        
        log.info("Normalized {} to TaxProfile: totalIncome={}, confidence={}%, sources={}",
                sourceType, profile.computeTotalGrossIncome(), profile.getValidation().getConfidenceScore(), 
                profile.getDocuments().getSourcesUsed());
        
        return profile;
    }
    
    /**
     * Convert a legacy Map<String, Object> (from AI fallback) into TaxProfile.
     */
    public TaxProfile normalizeFromLegacyMap(Map<String, Object> data, DocumentType sourceType) {
        TaxProfile profile = new TaxProfile();
        
        profile.getPersonalInfo().setPanNumber(getStr(data, "panNumber"));
        profile.getPersonalInfo().setAssesseeName(getStr(data, "assesseeName"));
        profile.getPersonalInfo().setFinancialYear(getStr(data, "financialYear"));
        
        profile.getIncome().setSalary(getDbl(data, "salaryIncome"));
        profile.getIncome().setInterestOther(getDbl(data, "interestIncome"));
        profile.getIncome().setDividend(getDbl(data, "dividendIncome"));
        profile.getIncome().setRent(getDbl(data, "rentIncome"));
        profile.getIncome().setCapitalGainsStcg(getDbl(data, "capitalGains"));
        profile.getIncome().setBusiness(getDbl(data, "businessIncome"));
        profile.getIncome().setGstTurnover(getDbl(data, "gstTurnover"));
        
        profile.getTaxes().setTotalTds(getDbl(data, "tds"));
        
        profile.getDocuments().getSourcesUsed().add(sourceType.name() + "_AI");
        profile.getValidation().setConfidenceScore(60); // AI fallback = lower confidence
        
        return profile;
    }
    
    // --- Helper methods ---
    
    private double getAmount(Map<String, ExtractedField> fields, String... keys) {
        for (String key : keys) {
            ExtractedField field = fields.get(key);
            if (field != null && field.getValue() != null) {
                if (field.getValue() instanceof Number) {
                    double val = ((Number) field.getValue()).doubleValue();
                    if (val > 0) return val;
                } else if (field.getValue() instanceof String) {
                    try {
                        String strVal = (String) field.getValue();
                        double val = Double.parseDouble(strVal.replaceAll("[^\\d.]", ""));
                        if (val > 0) return val;
                    } catch (Exception e) {
                        // ignore and continue
                    }
                }
            }
        }
        return 0;
    }
    
    private String getStringValue(Map<String, ExtractedField> fields, String... keys) {
        for (String key : keys) {
            ExtractedField field = fields.get(key);
            if (field != null && field.getValue() instanceof String) {
                String val = (String) field.getValue();
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }
    
    private double getDbl(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble(((String) val).replaceAll("[^\\d.]", "")); }
            catch (Exception e) { return 0; }
        }
        return 0;
    }
    
    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
