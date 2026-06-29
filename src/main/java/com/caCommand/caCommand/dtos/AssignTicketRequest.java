package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.Size;

public record AssignTicketRequest(
        @Size(max = 20, message = "Priority must be 20 characters or fewer")
        String priority,

        @Size(max = 1000, message = "Notes must be 1000 characters or fewer")
        String notes
) {
}
