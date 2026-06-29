package com.caCommand.caCommand.services.pipeline.parsers.models;

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
public class DocumentOCRResult {
    private String text;
    
    @Builder.Default
    private List<String> tables = new ArrayList<>();     // Raw table text blocks
    
    @Builder.Default
    private float confidence = 0.0f;                     // OCR confidence 0-1
    
    private int pages;
    private boolean scanned;                              // true if OCR was needed
    private String ocrEngine;                             // "PDFBOX", "GOOGLE_DOCUMENT_AI", "VISION_LLM"
    private long processingTimeMs;
    
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
    
    public static DocumentOCRResult empty() {
        return DocumentOCRResult.builder()
                .text("")
                .confidence(0.0f)
                .pages(0)
                .scanned(false)
                .build();
    }
    
    public static DocumentOCRResult fromPdfBox(String text, int pages) {
        return DocumentOCRResult.builder()
                .text(text)
                .confidence(0.95f)
                .pages(pages)
                .scanned(false)
                .ocrEngine("PDFBOX")
                .build();
    }
    
    public static DocumentOCRResult fromGoogleDocAI(String text, int pages, float confidence) {
        return DocumentOCRResult.builder()
                .text(text)
                .confidence(confidence)
                .pages(pages)
                .scanned(true)
                .ocrEngine("GOOGLE_DOCUMENT_AI")
                .build();
    }
    
    public static DocumentOCRResult fromVisionLLM(String text, int pages) {
        return DocumentOCRResult.builder()
                .text(text)
                .confidence(0.70f)
                .pages(pages)
                .scanned(true)
                .ocrEngine("VISION_LLM")
                .build();
    }
}
