package com.caCommand.caCommand.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Income {
    @Builder.Default private double salary = 0;
    @Builder.Default private double interestSavings = 0;
    @Builder.Default private double interestFd = 0;
    @Builder.Default private double interestOther = 0;
    @Builder.Default private double dividend = 0;
    @Builder.Default private double capitalGainsStcg = 0;
    @Builder.Default private double capitalGainsLtcg = 0;
    @Builder.Default private double rent = 0;
    @Builder.Default private double business = 0;
    @Builder.Default private double gstTurnover = 0;
    @Builder.Default private double agriculture = 0;
    @Builder.Default private double other = 0;
    @Builder.Default private int capitalGainsLineCount = 0;

    public double getTotalGrossIncome() {
        return salary + interestSavings + interestFd + interestOther + 
               dividend + rent + other; 
        // Note: capital gains and business usually require separate schedule computation 
        // and aren't directly summed into basic gross total income for simple thresholding.
    }
    
    public double getTotalInterest() {
        return interestSavings + interestFd + interestOther;
    }
    
    public double getTotalCapitalGains() {
        return capitalGainsStcg + capitalGainsLtcg;
    }
}
