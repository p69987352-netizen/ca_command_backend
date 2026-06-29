package com.caCommand.caCommand.services.ocr;

import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentOCRResult;
import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.ProcessorName;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Google Document AI OCR Service.
 * 
 * Setup Steps:
 * 1. Create a Google Cloud project at https://console.cloud.google.com
 * 2. Enable Document AI API
 * 3. Create an OCR Processor in Document AI console
 * 4. Create a Service Account and download JSON key
 * 5. Set environment variable: GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
 * 6. Configure application.properties:
 *    google.documentai.project-id=your-project-id
 *    google.documentai.location=us
 *    google.documentai.processor-id=your-processor-id
 *    google.documentai.enabled=true
 */
@Slf4j
@Service
public class GoogleDocumentAIService {

    @Value("${google.documentai.project-id:}")
    private String projectId;

    @Value("${google.documentai.location:us}")
    private String location;

    @Value("${google.documentai.processor-id:}")
    private String processorId;

    @Value("${google.documentai.enabled:false}")
    private boolean enabled;

    /**
     * Check if Google Document AI is configured and ready to use.
     */
    public boolean isAvailable() {
        return enabled 
               && projectId != null && !projectId.isBlank()
               && processorId != null && !processorId.isBlank();
    }

    /**
     * Extract text from a PDF using Google Document AI.
     * Returns DocumentOCRResult with structured output.
     * 
     * Currently a placeholder - will be fully implemented when user configures GCP.
     * When Google Cloud Document AI dependency is added to pom.xml, this method
     * will use DocumentProcessorServiceClient to process documents.
     * 
     * Future implementation:
     * <pre>
     * String endpoint = location + "-documentai.googleapis.com:443";
     * DocumentProcessorServiceSettings settings = DocumentProcessorServiceSettings.newBuilder()
     *     .setEndpoint(endpoint).build();
     * try (DocumentProcessorServiceClient client = DocumentProcessorServiceClient.create(settings)) {
     *     RawDocument rawDocument = RawDocument.newBuilder()
     *         .setContent(ByteString.copyFrom(pdfBytes))
     *         .setMimeType("application/pdf")
     *         .build();
     *     ProcessRequest request = ProcessRequest.newBuilder()
     *         .setName(ProcessorName.of(projectId, location, processorId).toString())
     *         .setRawDocument(rawDocument)
     *         .build();
     *     ProcessResponse response = client.processDocument(request);
     *     Document document = response.getDocument();
     *     return DocumentOCRResult.fromGoogleDocAI(
     *         document.getText(),
     *         document.getPagesCount(),
     *         (float) document.getPages(0).getConfidence()
     *     );
     * }
     * </pre>
     */
    public DocumentOCRResult extractText(byte[] pdfBytes) {
        if (!isAvailable()) {
            log.debug("Google Document AI is not configured. Skipping.");
            return DocumentOCRResult.empty();
        }

        long startTime = System.currentTimeMillis();
        log.info("Sending document to Google Document AI (Processor: {}, Location: {})", processorId, location);
        
        String endpoint = String.format("%s-documentai.googleapis.com:443", location);
        try {
            DocumentProcessorServiceSettings settings = DocumentProcessorServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build();
            
            try (DocumentProcessorServiceClient client = DocumentProcessorServiceClient.create(settings)) {
                RawDocument rawDocument = RawDocument.newBuilder()
                        .setContent(ByteString.copyFrom(pdfBytes))
                        .setMimeType("application/pdf")
                        .build();

                String name = ProcessorName.of(projectId, location, processorId).toString();
                ProcessRequest request = ProcessRequest.newBuilder()
                        .setName(name)
                        .setRawDocument(rawDocument)
                        .build();

                ProcessResponse response = client.processDocument(request);
                Document document = response.getDocument();

                float confidence = 0.90f; // Default high confidence if not provided at page level
                if (document.getPagesCount() > 0 && document.getPages(0).hasLayout() && document.getPages(0).getLayout().getConfidence() > 0) {
                    confidence = document.getPages(0).getLayout().getConfidence();
                }

                log.info("Google Document AI extraction complete. Pages: {}, Confidence: {}, Time: {}ms", 
                        document.getPagesCount(), confidence, (System.currentTimeMillis() - startTime));
                
                DocumentOCRResult result = DocumentOCRResult.fromGoogleDocAI(
                        document.getText(),
                        document.getPagesCount(),
                        confidence
                );
                result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                return result;
            }
        } catch (Exception e) {
            log.error("Google Document AI OCR failed: {}", e.getMessage(), e);
            return DocumentOCRResult.empty();
        }
    }
}
