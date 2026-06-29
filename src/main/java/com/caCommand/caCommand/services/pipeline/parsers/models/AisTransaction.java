package com.caCommand.caCommand.services.pipeline.parsers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AisTransaction {
    private String category;
    private String description;
    private String informationSource;
    private double amount;
    private String date;
}
