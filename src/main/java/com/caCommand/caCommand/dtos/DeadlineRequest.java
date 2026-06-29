package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DeadlineRequest(
        @NotNull(message = "Deadline is required")
        @Future(message = "Deadline must be in the future")
        LocalDateTime deadlineAt,

        @Size(max = 20, message = "Priority must be 20 characters or fewer")
        String priority,

        String note
) {
}
