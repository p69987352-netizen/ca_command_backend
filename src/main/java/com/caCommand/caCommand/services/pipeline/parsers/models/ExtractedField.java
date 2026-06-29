package com.caCommand.caCommand.services.pipeline.parsers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedField {
    private Object value;
    private int confidence; // 0 to 100
    private String sourceLocation;
    private String extractorUsed;

    public static ExtractedField of(Object value, int confidence, String sourceLocation, String extractorUsed) {
        return new ExtractedField(value, confidence, sourceLocation, extractorUsed);
    }
}
