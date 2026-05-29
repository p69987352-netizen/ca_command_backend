package com.caCommand.caCommand.dtos;

import lombok.Data;

@Data
public class FinalDeliveryRequest {
    private String finalDocumentUrl; // AWS S3 link of the final ITR receipt
    private String closingMessage;   // e.g., "Here is your filed ITR receipt!"
}