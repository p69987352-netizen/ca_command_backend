package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        @NotBlank(message = "Recipient is required")
        String recipient,

        @NotBlank(message = "Note is required")
        @Size(max = 1000, message = "Note must be 1000 characters or fewer")
        String note
) {
}
