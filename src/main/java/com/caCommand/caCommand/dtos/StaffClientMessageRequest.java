package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffClientMessageRequest(
        @NotBlank(message = "Message type is required")
        String type,

        @NotBlank(message = "Message is required")
        @Size(max = 1000, message = "Message must be 1000 characters or fewer")
        String message
) {
}
