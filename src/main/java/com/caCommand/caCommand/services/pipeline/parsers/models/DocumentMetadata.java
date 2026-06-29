package com.caCommand.caCommand.services.pipeline.parsers.models;

import com.caCommand.caCommand.enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    private DocumentType documentType;
    private String financialYear;
    private String assessmentYear;
    private String parserVersion;
    private int confidence;
    private int pageCount;
    private long extractionTimeMs;
    private String sourceFile;
    private String checksum;
}
