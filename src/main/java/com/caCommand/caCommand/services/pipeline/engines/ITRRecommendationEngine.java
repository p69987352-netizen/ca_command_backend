package com.caCommand.caCommand.services.pipeline.engines;

import com.caCommand.caCommand.models.ItrRecommendation;
import com.caCommand.caCommand.models.TaxProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ITRRecommendationEngine {

    public ItrRecommendation recommend(TaxProfile profile) {
        ItrRecommendation rec = new ItrRecommendation();
        double totalIncome = profile.computeTotalGrossIncome();
        
        // Rule 1: Business/Professional Income → ITR-3 or 4
        if (profile.getIncome().getBusiness() > 0) {
            rec.getReasons().add("✔ Business Income detected");
            if (totalIncome <= 5000000 && profile.getIncome().getTotalCapitalGains() == 0) {
                rec.setRecommendedItr("ITR-4");
                rec.setConfidence(90);
                rec.getReasons().add("✔ Eligible for Presumptive Taxation (Income ≤ 50L)");
                return rec;
            }
            rec.setRecommendedItr("ITR-3");
            rec.setConfidence(95);
            return rec;
        } else {
            rec.getReasons().add("✖ No Business Income found");
        }
        
        // Rule 2: Capital Gains → ITR-2
        if (profile.getIncome().getTotalCapitalGains() > 0) {
            rec.setRecommendedItr("ITR-2");
            rec.setConfidence(98);
            rec.getReasons().add("✔ Capital Gain Income detected");
            if (profile.getIncome().getDividend() > 0) rec.getReasons().add("✔ Dividend Income Present");
            if (profile.getIncome().getSalary() > 0) rec.getReasons().add("✔ Salary Income Present");
            return rec;
        }
        
        // Rule 3: Income > 50 Lakh → ITR-2
        if (totalIncome > 5000000) {
            rec.setRecommendedItr("ITR-2");
            rec.setConfidence(99);
            rec.getReasons().add("✔ Total income > 50 Lakhs");
            return rec;
        }
        
        // Rule 5: Agricultural income > 5000 → ITR-2
        if (profile.getIncome().getAgriculture() > 5000) {
            rec.setRecommendedItr("ITR-2");
            rec.setConfidence(95);
            rec.getReasons().add("✔ Agricultural income > ₹5,000");
            return rec;
        }
        
        // Default: Simple salary/pension + interest + dividend ≤ 50L → ITR-1
        rec.setRecommendedItr("ITR-1");
        rec.setConfidence(99);
        rec.getReasons().add("✔ Simple Salary/Pension Income");
        rec.getReasons().add("✔ Total income ≤ 50 Lakhs");
        return rec;
    }
}
