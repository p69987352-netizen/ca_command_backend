package com.caCommand.caCommand.services.pipeline.parsers.models;

import com.caCommand.caCommand.enums.DocumentType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentContext {
    private String rawText;
    private byte[] pdfBytes;
    private String filename;
    private int pages;
    private DocumentType type;
    private String assessmentYear;
    private String source;
}
