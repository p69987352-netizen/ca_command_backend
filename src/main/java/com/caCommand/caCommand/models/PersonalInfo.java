package com.caCommand.caCommand.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalInfo {
    private String panNumber;
    private String assesseeName;
    private String financialYear;
    private String assessmentYear;
    private String dateOfBirth;
    private String address;
    private String email;
    private String phone;
}
