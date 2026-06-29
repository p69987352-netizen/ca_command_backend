package com.caCommand.caCommand.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    @Builder.Default private int confidenceScore = 0; // 0-100
    @Builder.Default private List<String> warnings = new ArrayList<>();
    @Builder.Default private List<String> errors = new ArrayList<>();
    @Builder.Default private List<String> crossChecks = new ArrayList<>();
    @Builder.Default private List<String> missingFields = new ArrayList<>();

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addError(String error) {
        errors.add(error);
    }
    
    public void addCrossCheck(String check) {
        crossChecks.add(check);
    }
    
    public void addMissingField(String field) {
        missingFields.add(field);
    }
}
