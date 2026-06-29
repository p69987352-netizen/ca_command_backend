package com.caCommand.caCommand.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StaffRequest {

    @NotBlank(message = "Staff name is required")
    @Size(max = 100, message = "Staff name must be 100 characters or fewer")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[1-9][0-9]{7,14}$", message = "Phone number must be in international format without '+'")
    private String phoneNumber;
}
