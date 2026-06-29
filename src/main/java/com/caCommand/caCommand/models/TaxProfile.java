package com.caCommand.caCommand.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import com.caCommand.caCommand.services.pipeline.parsers.models.DeductorEntry;
import com.caCommand.caCommand.services.pipeline.parsers.models.SummaryTableRow;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfile {
    
    @Builder.Default private PersonalInfo personalInfo = new PersonalInfo();
    @Builder.Default private Income income = new Income();
    @Builder.Default private Taxes taxes = new Taxes();
    @Builder.Default private DocumentsMetadata documents = new DocumentsMetadata();
    @Builder.Default private ValidationResult validation = new ValidationResult();
    
    // Rich Extracted Data
    @Builder.Default private List<DeductorEntry> deductors = new ArrayList<>();
    @Builder.Default private List<SummaryTableRow> tisRows = new ArrayList<>();
    
    public double computeTotalGrossIncome() {
        return income.getTotalGrossIncome();
    }
    
    public void mergeFrom(TaxProfile other) {
        // AI Fallback creates flat profiles, but normal parsers will create nested objects.
        // For merging, we just take the max values for income.
        this.income.setSalary(Math.max(this.income.getSalary(), other.income.getSalary()));
        this.income.setInterestSavings(Math.max(this.income.getInterestSavings(), other.income.getInterestSavings()));
        this.income.setInterestFd(Math.max(this.income.getInterestFd(), other.income.getInterestFd()));
        this.income.setInterestOther(Math.max(this.income.getInterestOther(), other.income.getInterestOther()));
        this.income.setDividend(Math.max(this.income.getDividend(), other.income.getDividend()));
        this.income.setRent(Math.max(this.income.getRent(), other.income.getRent()));
        this.income.setCapitalGainsStcg(Math.max(this.income.getCapitalGainsStcg(), other.income.getCapitalGainsStcg()));
        this.income.setCapitalGainsLtcg(Math.max(this.income.getCapitalGainsLtcg(), other.income.getCapitalGainsLtcg()));
        this.income.setBusiness(Math.max(this.income.getBusiness(), other.income.getBusiness()));
        this.income.setGstTurnover(Math.max(this.income.getGstTurnover(), other.income.getGstTurnover()));
        this.income.setOther(Math.max(this.income.getOther(), other.income.getOther()));
        
        // Deductions & Tax
        this.taxes.setTotalTds(Math.max(this.taxes.getTotalTds(), other.taxes.getTotalTds()));
        this.taxes.setAdvanceTax(Math.max(this.taxes.getAdvanceTax(), other.taxes.getAdvanceTax()));
        this.taxes.setSelfAssessmentTax(Math.max(this.taxes.getSelfAssessmentTax(), other.taxes.getSelfAssessmentTax()));
        this.taxes.setOutstandingDemand(Math.max(this.taxes.getOutstandingDemand(), other.taxes.getOutstandingDemand()));
        this.taxes.setRefund(Math.max(this.taxes.getRefund(), other.taxes.getRefund()));
        
        // Merge tds by section
        other.taxes.getTdsBySection().forEach((section, amount) -> {
            this.taxes.getTdsBySection().put(section, Math.max(this.taxes.getTdsBySection().getOrDefault(section, 0.0), amount));
        });
        
        // Merge metadata
        for (String src : other.documents.getSourcesUsed()) {
            if (!this.documents.getSourcesUsed().contains(src)) this.documents.getSourcesUsed().add(src);
        }
        this.documents.setTisParsed(this.documents.isTisParsed() || other.documents.isTisParsed());
        this.documents.setForm26asParsed(this.documents.isForm26asParsed() || other.documents.isForm26asParsed());
        this.documents.setAisParsed(this.documents.isAisParsed() || other.documents.isAisParsed());
        this.documents.setDeductorCount(Math.max(this.documents.getDeductorCount(), other.documents.getDeductorCount()));
        
        // Merge rich data
        if (other.deductors != null && !other.deductors.isEmpty()) {
            this.deductors.addAll(other.deductors);
        }
        if (other.tisRows != null && !other.tisRows.isEmpty()) {
            this.tisRows.addAll(other.tisRows);
        }
        
        // Identity: prefer non-empty
        if (this.personalInfo.getPanNumber() == null || this.personalInfo.getPanNumber().isEmpty()) 
            this.personalInfo.setPanNumber(other.personalInfo.getPanNumber());
        if (this.personalInfo.getAssesseeName() == null || this.personalInfo.getAssesseeName().isEmpty()) 
            this.personalInfo.setAssesseeName(other.personalInfo.getAssesseeName());
        if (this.personalInfo.getFinancialYear() == null || this.personalInfo.getFinancialYear().isEmpty()) 
            this.personalInfo.setFinancialYear(other.personalInfo.getFinancialYear());
            
        // Validation confidence
        this.validation.setConfidenceScore(Math.max(this.validation.getConfidenceScore(), other.validation.getConfidenceScore()));
        this.validation.getWarnings().addAll(other.validation.getWarnings());
    }
    
    // Adapter method for AI Prompt backward compatibility
    public java.util.Map<String, Object> toLegacyMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("panNumber", personalInfo.getPanNumber() != null ? personalInfo.getPanNumber() : "");
        map.put("assesseeName", personalInfo.getAssesseeName() != null ? personalInfo.getAssesseeName() : "");
        map.put("financialYear", personalInfo.getFinancialYear() != null ? personalInfo.getFinancialYear() : "");
        
        map.put("salaryIncome", income.getSalary());
        map.put("interestIncome", income.getTotalInterest());
        map.put("dividendIncome", income.getDividend());
        map.put("rentIncome", income.getRent());
        map.put("capitalGains", income.getTotalCapitalGains());
        map.put("businessIncome", income.getBusiness());
        map.put("otherIncome", income.getOther());
        map.put("gstTurnover", income.getGstTurnover());
        
        map.put("tds", taxes.getTotalTds());
        map.put("totalIncome", computeTotalGrossIncome());
        return map;
    }
}
