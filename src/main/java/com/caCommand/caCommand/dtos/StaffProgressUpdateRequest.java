package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffProgressUpdateRequest(
        @NotNull(message = "Progress percent is required")
        @Min(value = 0, message = "Progress cannot be less than 0")
        @Max(value = 100, message = "Progress cannot be greater than 100")
        Integer progressPercent,

        @NotBlank(message = "Update message is required")
        @Size(max = 1000, message = "Update message must be 1000 characters or fewer")
        String updateMessage
) {
}
