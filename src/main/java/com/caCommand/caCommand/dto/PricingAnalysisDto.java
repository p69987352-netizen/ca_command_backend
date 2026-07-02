package com.caCommand.caCommand.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PricingAnalysisDto {
    private double competitorBenchmark;
    private double standardDiscount;
    private double baseFee;
    private List<ComplexityAdjustment> complexityAdjustments = new ArrayList<>();
    private double recommendedFee;
    private int confidence;
    private int version;

    @Data
    public static class ComplexityAdjustment {
        private String reason;
        private double amount;
        
        public ComplexityAdjustment(String reason, double amount) {
            this.reason = reason;
            this.amount = amount;
        }
    }
}
