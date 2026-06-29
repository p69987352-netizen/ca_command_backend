package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.common.exceptions.DocumentProcessingException;
import com.caCommand.caCommand.services.S3StorageService;
import com.caCommand.caCommand.services.ocr.GoogleDocumentAIService;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentOCRResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * 3-Layer OCR Service
 * 
 * Layer 1: PDFBox (free, instant, text-based PDFs)
 *     ↓ (if blank)
 * Layer 2: Google Document AI (fast, accurate, scanned PDFs)
 *     ↓ (if unavailable or fails)
 * Layer 3: Vision LLM (existing fallback, safety net)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OCRService {

    private final S3StorageService s3StorageService;
    private final com.caCommand.caCommand.services.ai.AIProviderService aiProviderService;
    private final GoogleDocumentAIService googleDocumentAIService;

    public byte[] downloadDocument(String documentUrl) {
        java.io.File tempFile = null;
        try {
            tempFile = s3StorageService.downloadMediaLocally(documentUrl);
            return java.nio.file.Files.readAllBytes(tempFile.toPath());
        } catch (Exception e) {
            log.error("Failed to download document from S3", e);
            throw new DocumentProcessingException("Could not download document for OCR", e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * New method: Returns structured DocumentOCRResult with confidence, engine info, etc.
     */
    public DocumentOCRResult extractWithOCR(byte[] pdfBytes, String password) {
        long startTime = System.currentTimeMillis();

        // ===== LAYER 1: PDFBox (free, instant) =====
        try {
            DocumentOCRResult pdfBoxResult = extractWithPdfBox(pdfBytes, password);
            if (pdfBoxResult.hasText()) {
                pdfBoxResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                log.info("Layer 1 SUCCESS: PDFBox extracted {} chars in {}ms", 
                        pdfBoxResult.getText().length(), pdfBoxResult.getProcessingTimeMs());
                return pdfBoxResult;
            }
            log.info("Layer 1: PDFBox returned blank text. Trying Layer 2...");
        } catch (Exception e) {
            log.warn("Layer 1 FAILED: PDFBox error: {}", e.getMessage());
        }

        // ===== LAYER 2: Google Document AI (accurate, paid) =====
        // DISABLED AS PER USER REQUEST (Billing Issue / Not Needed)
        /*
        try {
            DocumentOCRResult googleResult = googleDocumentAIService.extractText(pdfBytes);
            if (googleResult.hasText()) {
                googleResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                log.info("Layer 2 SUCCESS: Google Document AI extracted {} chars in {}ms",
                        googleResult.getText().length(), googleResult.getProcessingTimeMs());
                return googleResult;
            }
            log.info("Layer 2: Google Document AI returned blank/unavailable. Trying Layer 3...");
        } catch (Exception e) {
            log.warn("Layer 2 FAILED: Google Document AI error: {}", e.getMessage());
        }
        */

        // ===== LAYER 3: Vision LLM (fallback, render PDF pages to image) =====
        try {
            DocumentOCRResult visionResult = extractWithVisionLLM(pdfBytes, password);
            visionResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            log.info("Layer 3 {}: Vision LLM extracted {} chars in {}ms",
                    visionResult.hasText() ? "SUCCESS" : "EMPTY",
                    visionResult.getText() != null ? visionResult.getText().length() : 0,
                    visionResult.getProcessingTimeMs());
            return visionResult;
        } catch (Exception e) {
            log.error("Layer 3 FAILED: Vision LLM error: {}", e.getMessage());
            DocumentOCRResult empty = DocumentOCRResult.empty();
            empty.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return empty;
        }
    }

    /**
     * Legacy method: Returns plain text. Kept for backward compatibility.
     */
    public String extractTextFromPdf(byte[] pdfBytes, String password) {
        DocumentOCRResult result = extractWithOCR(pdfBytes, password);
        return result.hasText() ? result.getText() : "";
    }

    // ===== LAYER 1: PDFBox =====
    private DocumentOCRResult extractWithPdfBox(byte[] pdfBytes, String password) throws Exception {
        try (PDDocument document = loadPdf(pdfBytes, password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            int pages = document.getNumberOfPages();
            
            if (text != null && !text.trim().isEmpty()) {
                return DocumentOCRResult.fromPdfBox(text.trim(), pages);
            }
            
            // Return empty but with page count for Layer 3
            return DocumentOCRResult.builder()
                    .text("")
                    .pages(pages)
                    .scanned(true)
                    .build();
        }
    }

    // ===== LAYER 3: Vision LLM (render pages to images, send to AI) =====
    private DocumentOCRResult extractWithVisionLLM(byte[] pdfBytes, String password) {
        try {
            PDDocument document = null;
            try {
                document = loadPdf(pdfBytes, password);
            } catch (Exception e) {
                // Not a valid PDF — treat as raw image
                log.warn("PDFBox failed to load. Treating as raw image for Vision OCR.");
                String base64Image = java.util.Base64.getEncoder().encodeToString(pdfBytes);
                String prompt = "Extract all text exactly as it appears in this document image. Return ONLY the text, no conversational filler.";
                String pageText = aiProviderService.generateTextFromImage(prompt, base64Image);
                return DocumentOCRResult.fromVisionLLM(pageText, 1);
            }

            try {
                log.info("PDF parsed but text is blank. Fallback to Vision OCR page by page.");
                org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                int numPages = Math.min(document.getNumberOfPages(), 5);
                
                StringBuilder fullText = new StringBuilder();
                for (int i = 0; i < numPages; i++) {
                    try {
                        java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150, 
                                org.apache.pdfbox.rendering.ImageType.RGB);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(img, "jpg", baos);
                        String base64Image = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                        String prompt = "Extract all text exactly as it appears in this document page. Return ONLY the text, no conversational filler.";
                        String pageText = aiProviderService.generateTextFromImage(prompt, base64Image);
                        fullText.append(pageText).append("\n\n");
                    } catch (Exception ex) {
                        log.warn("Failed to OCR page {}: {}", i, ex.getMessage());
                    }
                }
                return DocumentOCRResult.fromVisionLLM(fullText.toString(), numPages);
            } finally {
                document.close();
            }
        } catch (Exception e) {
            log.error("Vision LLM OCR completely failed", e);
            throw new DocumentProcessingException("Failed to extract text from document", e);
        }
    }

    private PDDocument loadPdf(byte[] pdfBytes, String password) throws Exception {
        if (password != null && !password.isEmpty()) {
            try {
                return PDDocument.load(pdfBytes, password);
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                log.warn("Invalid password provided for PDF, attempting without password...");
                return PDDocument.load(pdfBytes);
            }
        } else {
            return PDDocument.load(pdfBytes);
        }
    }
}
