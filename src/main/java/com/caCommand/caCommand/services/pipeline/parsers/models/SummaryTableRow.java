package com.caCommand.caCommand.services.pipeline.parsers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one row from TIS/AIS summary table.
 * 
 * Example TIS row:
 *   SR. NO.  INFORMATION CATEGORY          PROCESSED BY SYSTEM  ACCEPTED BY TAXPAYER
 *   1        Rent received                 44,73,825            44,73,825
 *   2        Dividend                      1,07,547             1,07,547
 *   3        Interest from savings bank    35,257               35,257
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryTableRow {
    private int srNo;
    private String category;           // "Interest from savings bank"
    private double processedValue;     // Value processed by system
    private double acceptedValue;      // Value accepted by taxpayer / confirmed by source
    private String section;            // "SUMMARY" or "ANNEXURE"
    private int confidence;            // 0-100
    
    /**
     * Returns the best available value (accepted > processed).
     */
    public double getBestValue() {
        return acceptedValue > 0 ? acceptedValue : processedValue;
    }
    
    @Override
    public String toString() {
        return String.format("%-4d %-45s %,15.0f  %,15.0f  [%s]", 
                srNo, category, processedValue, acceptedValue, section);
    }
}
