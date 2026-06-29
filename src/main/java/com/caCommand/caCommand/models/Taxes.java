package com.caCommand.caCommand.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Taxes {
    @Builder.Default private double totalTds = 0;
    @Builder.Default private double advanceTax = 0;
    @Builder.Default private double selfAssessmentTax = 0;
    @Builder.Default private double refund = 0;
    @Builder.Default private double outstandingDemand = 0;
    
    // Detailed TDS breakdown by section (e.g., "194A" -> 5000.0)
    @Builder.Default private Map<String, Double> tdsBySection = new HashMap<>();
    
    public void addTds(String section, double amount) {
        if (section == null || section.isBlank()) {
            section = "OTHER";
        }
        tdsBySection.put(section, tdsBySection.getOrDefault(section, 0.0) + amount);
        totalTds += amount;
    }
}
