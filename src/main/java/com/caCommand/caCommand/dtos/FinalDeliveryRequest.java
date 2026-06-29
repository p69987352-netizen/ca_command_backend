package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinalDeliveryRequest {

    @Size(max = 2048, message = "Final document URL is too long")
    private String finalDocumentUrl; // AWS S3 link of the final ITR receipt

    @Size(max = 1000, message = "Closing message is too long")
    private String closingMessage;   // e.g., "Here is your filed ITR receipt!"
}
