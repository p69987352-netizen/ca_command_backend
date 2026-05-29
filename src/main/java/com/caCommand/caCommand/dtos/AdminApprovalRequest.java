package com.caCommand.caCommand.dtos;

import lombok.Data;

@Data
public class AdminApprovalRequest {
    private Double feeAmount;
    private String adminNotes;
}