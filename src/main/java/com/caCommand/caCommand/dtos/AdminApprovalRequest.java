package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminApprovalRequest {

    @NotNull(message = "Fee amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Fee amount must be greater than zero")
    private Double feeAmount;

    private String adminNotes;
    
    private String overrideReason;
    
    private String overriddenBy;
}
