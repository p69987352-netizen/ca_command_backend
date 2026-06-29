package com.caCommand.caCommand.services.pipeline.parsers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one deductor row from Form 26AS Part A/A1.
 * 
 * Example 26AS deductor block:
 *   Name of Deductor:  BANK OF BARODA
 *   TAN:               UDPB04255G
 *   Section:           194A
 *   Amount Paid:       2,91,090
 *   Tax Deducted:      29,109
 *   TDS Deposited:     29,109
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeductorEntry {
    private String name;              // "BANK OF BARODA"
    private String tan;               // "UDPB04255G"
    private String section;           // "194A", "194", "192" etc.
    private double amountPaid;        // Total amount paid/credited
    private double tdsDeducted;       // Tax deducted at source
    private double tdsDeposited;      // Tax deposited to government
    private int confidence;
    
    @Override
    public String toString() {
        return String.format("%-30s Sec:%-6s  Paid: %,12.0f  TDS: %,10.0f  Deposited: %,10.0f", 
                name != null ? name : "Unknown", 
                section != null ? section : "-", 
                amountPaid, tdsDeducted, tdsDeposited);
    }
}
