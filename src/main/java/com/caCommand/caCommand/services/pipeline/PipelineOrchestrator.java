package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.ExtractedData;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.models.TaxProfile;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.ExtractedDataRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.services.ai.AIProviderService;
import com.caCommand.caCommand.services.cache.CacheService;
import com.caCommand.caCommand.services.pipeline.engines.ComplexityEngine;
import com.caCommand.caCommand.services.pipeline.engines.ITRRecommendationEngine;
import com.caCommand.caCommand.services.pipeline.normalizer.TaxProfileNormalizer;
import com.caCommand.caCommand.services.pipeline.normalizer.ConfidenceEngine;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentOCRResult;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;
import com.caCommand.caCommand.services.pricing.PricingEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.caCommand.caCommand.services.S3StorageService;

/**
 * Pipeline Orchestrator - 12/10 Enterprise Architecture
 * 
 * Pipeline Flow:
 * Upload → PasswordResolver → DocumentLoader → 3-Layer OCR
 *     → Parser (AIS/TIS/Form16/26AS) → Normalizer → Unified TaxProfile
 *     → ITR Recommendation → Complexity Engine → Pricing Engine
 *     → AI Analysis → Dashboard + WhatsApp
 * 
 * Orchestrator ONLY orchestrates. It does NOT:
 * - Touch ClientRepository directly (PasswordResolver does that)
 * - Generate passwords (PasswordResolver does that)
 * - Determine ITR form (ITRRecommendationEngine does that)
 * - Calculate complexity (ComplexityEngine does that)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final OCRService ocrService;
    private final ValidationService validationService;
    private final DocumentClassifierService classifierService;
    private final com.caCommand.caCommand.services.analysis.ArjunAIAnalysisService analysisService;
    private final PricingEngineService pricingService;
    private final CacheService cacheService;
    private final TicketRepository ticketRepository;
    private final AIProviderService aiProviderService;
    private final ExtractedDataRepository extractedDataRepository;
    private final ClientRepository clientRepository;
    private final WorkflowEngineService workflowEngineService;
    private final com.caCommand.caCommand.services.pipeline.parsers.ParserFactory parserFactory;
    private final com.caCommand.caCommand.services.notification.NotificationService notificationService;
    
    // New Sprint 2.0 dependencies
    private final PasswordResolver passwordResolver;
    private final TaxProfileNormalizer taxProfileNormalizer;
    private final ITRRecommendationEngine itrRecommendationEngine;
    private final ComplexityEngine complexityEngine;
    private final DocumentUnlockService documentUnlockService;
    private final S3StorageService s3StorageService;
    private final com.caCommand.caCommand.services.pipeline.parsers.validators.CrossValidator crossValidator;
    private final ConfidenceEngine confidenceEngine;
    
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock> ticketLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @Async("taskExecutor")
    public void processTicketAsync(String ticketId) {
        java.util.concurrent.locks.ReentrantLock lock = ticketLocks.computeIfAbsent(ticketId, k -> new java.util.concurrent.locks.ReentrantLock());
        
        if (!lock.tryLock()) {
            log.info("Pipeline already running for ticket: {}. Marking hasPendingChanges = true", ticketId);
            setHasPendingChanges(ticketId, true);
            return;
        }

        try {
            log.info("Pipeline Lock Acquired for ticket: {}", ticketId);
            boolean runAgain = true;
            while (runAgain) {
                setHasPendingChanges(ticketId, false);
                
                com.caCommand.caCommand.models.PipelineContext context = com.caCommand.caCommand.models.PipelineContext.builder()
                        .ticketId(ticketId)
                        .pipelineExecutionId(java.util.UUID.randomUUID().toString())
                        .startedAt(java.time.Instant.now())
                        .build();

                executePipeline(ticketId, context);
                
                runAgain = checkHasPendingChanges(ticketId);
                if (runAgain) {
                    log.info("Pending changes detected for ticket: {}. Restarting pipeline.", ticketId);
                }
            }
        } finally {
            lock.unlock();
            log.info("Pipeline Lock Released for ticket: {}", ticketId);
        }
    }

    private void setHasPendingChanges(String ticketId, boolean hasPending) {
        ticketRepository.findById(java.util.UUID.fromString(ticketId)).ifPresent(ticket -> {
            ticket.setHasPendingChanges(hasPending);
            ticketRepository.save(ticket);
        });
    }

    private boolean checkHasPendingChanges(String ticketId) {
        return ticketRepository.findById(java.util.UUID.fromString(ticketId))
                .map(ticket -> ticket.getHasPendingChanges() != null && ticket.getHasPendingChanges())
                .orElse(false);
    }

    private void executePipeline(String ticketId, com.caCommand.caCommand.models.PipelineContext context) {
        log.info("Starting pipeline execution {} for ticket: {}", context.getPipelineExecutionId(), ticketId);
        Ticket ticket = ticketRepository.findById(java.util.UUID.fromString(ticketId)).orElse(null);
        if (ticket == null || ticket.getClientDocuments() == null || ticket.getClientDocuments().isEmpty()) {
            log.warn("Ticket {} has no documents to process", ticketId);
            return;
        }

        // --- NEW BYPASS LOGIC: Skip AI Extraction for Non-ITR Services ---
        if (ticket.getServiceType() != null && !ticket.getServiceType().equalsIgnoreCase("ITR Filing")) {
            log.info("Ticket {} is for Non-ITR service ({}). Bypassing AI processing pipeline.", ticketId, ticket.getServiceType());
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.SUCCESS);
            return;
        }

        try {
            // Setup Execution Context
            ticket.setPipelineExecutionId(context.getPipelineExecutionId());
            ticket.setPipelineStartedAt(java.time.LocalDateTime.now());
            ticketRepository.save(ticket);

            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.QUEUED);
            if (context.isCancelled()) return;

            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.LOCK_ACQUIRED);
            if (context.isCancelled()) return;
            
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.DOCUMENT_LOADING);
            if (context.isCancelled()) return;
            
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.VALIDATING);
            if (context.isCancelled()) return;

            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.OCR_RUNNING);
            
            // ===== UNIFIED TAX PROFILE (merges all documents) =====
            TaxProfile mergedProfile = new TaxProfile();
            TaxProfile tisOnlyProfile = new TaxProfile();
            String[] lines = ticket.getClientDocuments().split("\n");
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (context.isCancelled()) return;
                String[] parts = line.split(" :: ");
                if (parts.length < 2) continue;
                String expectedType = parts[0].trim();
                String documentUrl = parts[1].trim();
                
                // Resolve document type via enum (no string matching!)
                DocumentType docType = DocumentType.fromString(expectedType);
                log.info("Processing document type: {} ({}) for ticket: {}", docType, expectedType, ticketId);
                
                if (docType == DocumentType.AADHAAR || docType == DocumentType.PAN || docType == DocumentType.BANK_STATEMENT) {
                    log.info("Skipping OCR for document type: {}. Document marked as received.", docType);
                    continue;
                }

                // Download document
                byte[] pdfBytes = ocrService.downloadDocument(documentUrl);
                String hash = validationService.generateSha256(pdfBytes);
                
                // Resolve password (PasswordResolver handles Client lookup — Pipeline doesn't touch it)
                String password = passwordResolver.resolvePassword(ticketId, docType);
                
                // UNLOCK DOCUMENT IF NEEDED
                if ((docType == DocumentType.AIS || docType == DocumentType.TIS) && password != null && !password.isEmpty()) {
                    try {
                        byte[] unlockedBytes = documentUnlockService.unlockDocument(pdfBytes, password);
                        if (unlockedBytes != pdfBytes) {
                            pdfBytes = unlockedBytes;
                            // Upload back unlocked document
                            String newUrl = s3StorageService.uploadMedia(pdfBytes, "unlocked_" + UUID.randomUUID().toString() + ".pdf");
                            // Update ticket document URL
                            lines[i] = expectedType + " :: " + newUrl;
                            ticket.setClientDocuments(String.join("\n", lines));
                            if (docType == DocumentType.AIS) ticket.setAisPdfPath(newUrl);
                            ticketRepository.save(ticket);
                            log.info("Unlocked {} and updated ticket URL", expectedType);
                            // Clear password since it's now unlocked
                            password = "";
                        }
                    } catch (Exception e) {
                        log.warn("Failed to unlock document: {}", expectedType, e);
                    }
                }
                
                // Extract text via 3-layer OCR
                DocumentOCRResult ocrResult = ocrService.extractWithOCR(pdfBytes, password);
                log.info("OCR Engine: {}, Confidence: {}, Scanned: {}", 
                        ocrResult.getOcrEngine(), ocrResult.getConfidence(), ocrResult.isScanned());
                        
                // --- RE-CLASSIFY PROTECTED DOCUMENTS POST-DECRYPTION ---
                if (ocrResult.hasText()) {
                    String extractedTextLower = ocrResult.getText().toLowerCase();
                    if (extractedTextLower.contains("taxpayer information summary") || extractedTextLower.contains("tis")) {
                        if (docType == DocumentType.AIS) {
                            docType = DocumentType.TIS;
                            expectedType = "TIS Statement (PDF)";
                            log.info("Re-classified document from AIS to TIS based on extracted text.");
                        }
                    } else if (extractedTextLower.contains("annual information statement") || extractedTextLower.contains("ais")) {
                        if (docType == DocumentType.TIS) {
                            docType = DocumentType.AIS;
                            expectedType = "AIS Statement (PDF)";
                            log.info("Re-classified document from TIS to AIS based on extracted text.");
                        }
                    } else if (extractedTextLower.contains("form 16") || extractedTextLower.contains("form no. 16")) {
                        docType = DocumentType.FORM16;
                        expectedType = "Form 16";
                        log.info("Re-classified document to Form 16 based on extracted text.");
                    }
                }
                
                if (docType == DocumentType.AIS) {
                    log.info("Skipping data extraction for AIS. Using TIS as primary source.");
                    continue;
                }
                
                // Parse and normalize into TaxProfile
                TaxProfile docProfile = extractAndNormalize(pdfBytes, ocrResult, docType);
                
                // Merge into unified profile
                mergedProfile.mergeFrom(docProfile);
                if (docType == DocumentType.TIS) {
                    tisOnlyProfile.mergeFrom(docProfile);
                }
            }
            
            // ===== CROSS VALIDATION =====
            java.util.List<String> crossWarnings = crossValidator.validate(mergedProfile);
            mergedProfile.getValidation().getWarnings().addAll(crossWarnings);
            
            // ===== CONFIDENCE COMPUTATION =====
            int finalConfidence = confidenceEngine.calculateConfidence(mergedProfile);
            mergedProfile.getValidation().setConfidenceScore(finalConfidence);
            
            int fieldCount = countNonZeroFields(mergedProfile);

            if (context.isCancelled()) return;
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.CLASSIFICATION);
            
            // Determine which profile to use for logic (Prefer TIS, fallback to merged)
            TaxProfile logicProfile = tisOnlyProfile.getDocuments().getSourcesUsed().isEmpty() ? mergedProfile : tisOnlyProfile;

            // ===== ITR RECOMMENDATION =====
            com.caCommand.caCommand.models.ItrRecommendation itrRec = itrRecommendationEngine.recommend(logicProfile);
            String itrForm = itrRec.getRecommendedItr();
            
            if (context.isCancelled()) return;
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.RULE_ENGINE);
            
            // ===== COMPLEXITY ANALYSIS =====
            ComplexityEngine.ComplexityResult complexity = complexityEngine.evaluate(logicProfile, itrForm);
            
            if (context.isCancelled()) return;
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.AI_ANALYSIS);
            
            // AI Analysis uses legacy map for backward compatibility
            Map<String, Object> mergedJson = mergedProfile.toLegacyMap();
            String aiSummary = analysisService.generateRiskAnalysis(mergedJson);
            
            if (context.isCancelled()) return;
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.PRICING);
            
            // ===== SMART PRICING =====
            com.caCommand.caCommand.dto.PricingAnalysisDto pricingResult = pricingService.calculateFromProfile(logicProfile, itrForm);

            // Save everything
            saveExtractedDataAndUpdateTicket(ticketId, mergedProfile, itrForm, pricingResult.getRecommendedFee());

            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.SUCCESS);
            
            // ===== PRETTY PRINT UNIFIED TAX PROFILE =====
            prettyPrintFinalResult(mergedProfile, itrForm, pricingResult, complexity, fieldCount, crossWarnings);
            
            // Keep status as PENDING_ADMIN_APPROVAL (Do not automatically transition to AWAITING_PAYMENT)
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.TicketStatus.PENDING_ADMIN_APPROVAL);
            ticket.setQuotedFee(pricingResult.getRecommendedFee());
            ticketRepository.save(ticket);
            
            log.info("Pipeline completed for ticket: {} | ITR={} | Price=INR {} | Complexity={}/10",
                    ticketId, itrForm, pricingResult.getRecommendedFee(), complexity.getScore());

        } catch (Exception e) {
            log.error("Pipeline failed for ticket: {}", ticketId, e);
            workflowEngineService.transition(ticket, com.caCommand.caCommand.enums.PipelineStatus.FAILED);
            // DO NOT update business status to FAILED, let it stay in its current business state
        }
    }

    /**
     * Extract financial data from a document and normalize into TaxProfile.
     * Uses Native Parser first, falls back to AI.
     */
    private TaxProfile extractAndNormalize(byte[] pdfBytes, DocumentOCRResult ocrResult, DocumentType docType) {
        try {
            String documentText = ocrResult.hasText() ? ocrResult.getText() : "";
            
            // Try Native Parser first
            Optional<com.caCommand.caCommand.services.pipeline.parsers.DocumentParser> parserOpt = parserFactory.getParserForType(docType);
            
            if (parserOpt.isPresent()) {
                com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext docContext = 
                    com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext.builder()
                        .rawText(documentText)
                        .pdfBytes(pdfBytes)
                        .type(docType)
                        .build();

                ExtractionResult result = parserOpt.get().parse(docContext);
                
                log.info("Native Parser Engine finished with {}% confidence", result.getOverallConfidence());
                
                if (result.getOverallConfidence() >= 80) {
                    // Normalize ExtractionResult → TaxProfile
                    TaxProfile profile = taxProfileNormalizer.normalize(result, docType);
                    
                    // Fallback to AI if native parser extracted exactly 0 income for an income document (excluding 26AS which only has TDS)
                    // In v3, we check Confidence score primarily.
                    int confidence = confidenceEngine.calculateConfidence(profile);
                    profile.getValidation().setConfidenceScore(confidence);
                    
                    if (confidence >= 50) {
                        return profile;
                    } else {
                        log.warn("Native Parser confidence too low ({}%). Falling back to AI Engine for robust extraction.", confidence);
                    }
                } else {
                    log.warn("Native Parser confidence too low (< 80%), falling back to AI Engine.");
                }
            }

            // Fallback to AI Engine
            Map<String, Object> aiResult = extractViaAI(documentText, docType);
            TaxProfile aiProfile = taxProfileNormalizer.normalizeFromLegacyMap(aiResult, docType);
            aiProfile.getValidation().setConfidenceScore(confidenceEngine.calculateConfidence(aiProfile));
            return aiProfile;
            
        } catch (Exception e) {
            log.error("Extraction failed for docType={}: {}", docType, e.getMessage());
            TaxProfile emptyProfile = new TaxProfile();
            emptyProfile.getValidation().getWarnings().add("Extraction failed: " + e.getMessage());
            return emptyProfile;
        }
    }

    /**
     * AI-based extraction fallback.
     */
    private Map<String, Object> extractViaAI(String documentText, DocumentType docType) {
        try {
            // Safety check for very large documents (e.g., 11+ pages)
            if (documentText != null && documentText.length() > 100000) {
                log.warn("Document is very large ({} chars). Truncating to prevent AI token limit errors.", documentText.length());
                documentText = documentText.substring(0, 100000);
            }

            String prompt = String.format("""
                    You are an expert Indian tax document analyzer.
                    This is a '%s' document from the Income Tax Department of India.
                    
                    Extract ALL financial data from the following document text.
                    Return ONLY a valid JSON object with these keys (use 0 if not found):
                    {
                      "salaryIncome": <total salary/wages amount>,
                      "rentIncome": <total rent received amount>,
                      "dividendIncome": <total dividend amount>,
                      "interestIncome": <total interest from savings/deposits>,
                      "capitalGains": <actual capital gains amount ONLY. Do NOT put gross sale of securities/mutual funds here! Use 0 if only gross sales are present>,
                      "gstTurnover": <total GST turnover>,
                      "tds": <total TDS deducted>,
                      "totalIncome": <sum of all actual income categories (DO NOT add gross sales or GST turnover to this)>,
                      "panNumber": "<PAN number>",
                      "assesseeName": "<name of assessee>",
                      "financialYear": "<financial year>"
                    }
                    
                    IMPORTANT CRITICAL RULES:
                    - Do NOT perform manual math or addition. Look for the "Total", "Grand Total", or "Summary" (Part B) values.
                    - Always prioritize the "Processed Value" or "Reported Value" columns in the summary.
                    - Return plain numbers without commas (e.g., 1250000).
                    - If a category is missing entirely, use 0.
                    - Do NOT treat 'Sale of securities and units of mutual fund' or 'Purchase of securities' as Capital Gains or Income. They are gross transactions.
                    - Return ONLY the JSON, no explanation, no markdown code fences.
                    
                    --- DOCUMENT TEXT ---
                    %s
                    -----------------------
                    """, docType.getDisplayName(), documentText);

            String response = aiProviderService.generateText(prompt);
            return parseVisionResponse(response);
        } catch (Exception e) {
            log.error("AI extraction failed: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private int countNonZeroFields(TaxProfile p) {
        int count = 0;
        if (p.getPersonalInfo().getPanNumber() != null && !p.getPersonalInfo().getPanNumber().isEmpty()) count++;
        if (p.getPersonalInfo().getAssesseeName() != null && !p.getPersonalInfo().getAssesseeName().isEmpty()) count++;
        if (p.getIncome().getSalary() > 0) count++;
        if (p.getIncome().getTotalInterest() > 0) count++;
        if (p.getIncome().getDividend() > 0) count++;
        if (p.getIncome().getRent() > 0) count++;
        if (p.getIncome().getTotalCapitalGains() > 0) count++;
        if (p.getIncome().getBusiness() > 0) count++;
        if (p.getIncome().getGstTurnover() > 0) count++;
        if (p.getTaxes().getTotalTds() > 0) count++;
        return count;
    }

    /**
     * Saves extracted data and updates the ticket quoted fee and client income range.
     * Now uses TaxProfile instead of raw Map.
     */
    private synchronized void saveExtractedDataAndUpdateTicket(String ticketId, TaxProfile profile, String itrForm, double price) {
        try {
            Ticket ticket = ticketRepository.findById(java.util.UUID.fromString(ticketId)).orElse(null);
            if (ticket == null || ticket.getClient() == null) return;
            
            // Fetch Client explicitly to avoid LazyInitializationException in Async context
            Client client = clientRepository.findById(ticket.getClient().getId()).orElse(null);
            if (client == null) return;

            // 1. UPDATE TICKET
            if (ticket.getQuotedFee() == null || ticket.getQuotedFee() < price) {
                ticket.setQuotedFee(price);
                ticketRepository.save(ticket);
            }

            // 2. MERGE EXTRACTED DATA
            ExtractedData ed = extractedDataRepository.findFirstByClientIdOrderByCreatedAtDesc(client.getId())
                    .orElse(new ExtractedData());
            
            if (ed.getClient() == null) {
                ed.setClient(client);
            }

            ed.setDocumentType(String.join(" + ", profile.getDocuments().getSourcesUsed()));
            
            if (profile.getPersonalInfo().getFinancialYear() != null) ed.setFinancialYear(profile.getPersonalInfo().getFinancialYear());
            
            ed.setSalaryIncome(profile.getIncome().getSalary());
            ed.setInterestIncome(profile.getIncome().getTotalInterest());
            ed.setDividendIncome(profile.getIncome().getDividend());
            ed.setTds(profile.getTaxes().getTotalTds());
            ed.setCapitalGains(profile.getIncome().getTotalCapitalGains());
            
            double totalIncome = profile.computeTotalGrossIncome();
            
            // Set AIS/TIS reported income based on sources
            if (profile.getDocuments().getSourcesUsed().stream().anyMatch(s -> s.contains("AIS"))) {
                ed.setAisReportedIncome(totalIncome);
            }
            if (profile.getDocuments().getSourcesUsed().stream().anyMatch(s -> s.contains("TIS"))) {
                ed.setTisReportedIncome(totalIncome);
            }

            // Risk Score
            if (totalIncome > 1000000) {
                ed.setRiskScore("High");
            } else if (totalIncome > 500000) {
                ed.setRiskScore("Medium");
            } else {
                ed.setRiskScore("Low");
            }

            // Update Client Income Range
            if (totalIncome > 5000000) client.setIncomeRange("ABOVE_50L");
            else if (totalIncome > 2000000) client.setIncomeRange("20L_TO_50L");
            else if (totalIncome > 1000000) client.setIncomeRange("10L_TO_20L");
            else if (totalIncome > 500000) client.setIncomeRange("5L_TO_10L");
            else client.setIncomeRange("BELOW_5L");
            
            clientRepository.save(client);

            // ITR from engine
            ed.setSuggestedItr(itrForm);
            
            try {
                tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
                ed.setRawJson(mapper.writeValueAsString(profile));
            } catch (Exception ex) {
                log.warn("Failed to serialize TaxProfile to JSON, falling back to legacy map", ex);
                ed.setRawJson(profile.toLegacyMap().toString());
            }
            
            extractedDataRepository.save(ed);

            log.info("Saved TaxProfile → ExtractedData & Ticket quotedFee for client={}", client.getId());

        } catch (Exception e) {
            log.error("Failed to save ExtractedData and Ticket: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVisionResponse(String response) {
        try {
            String json = response.trim();
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }
            if (json.startsWith("{")) {
                tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
                return mapper.readValue(json, Map.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Vision AI response: {}", e.getMessage());
        }
        return new HashMap<>();
    }
    private void prettyPrintFinalResult(TaxProfile profile, String itrForm, 
                                        com.caCommand.caCommand.dto.PricingAnalysisDto pricing, 
                                        ComplexityEngine.ComplexityResult complexity, 
                                        int fieldCount, 
                                        java.util.List<String> crossWarnings) {
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║                  UNIFIED TAX PROFILE EXTRACTED                   ║");
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info(String.format("║ PAN:                 %-44s║", profile.getPersonalInfo().getPanNumber() != null ? profile.getPersonalInfo().getPanNumber() : "(missing)"));
        log.info(String.format("║ Name:                %-44s║", profile.getPersonalInfo().getAssesseeName() != null ? profile.getPersonalInfo().getAssesseeName() : "(missing)"));
        log.info(String.format("║ FY:                  %-44s║", profile.getPersonalInfo().getFinancialYear() != null ? profile.getPersonalInfo().getFinancialYear() : "(missing)"));
        log.info("║──────────────────────────────────────────────────────────────────║");
        log.info(String.format("║ Salary:              %14s                              ║", formatAmount(profile.getIncome().getSalary())));
        log.info(String.format("║ Interest:            %14s                              ║", formatAmount(profile.getIncome().getTotalInterest())));
        log.info(String.format("║ Dividend:            %14s                              ║", formatAmount(profile.getIncome().getDividend())));
        log.info(String.format("║ Rent:                %14s                              ║", formatAmount(profile.getIncome().getRent())));
        log.info(String.format("║ Capital Gains:       %14s                              ║", formatAmount(profile.getIncome().getTotalCapitalGains())));
        log.info(String.format("║ Business/Prof:       %14s                              ║", formatAmount(profile.getIncome().getBusiness())));
        log.info(String.format("║ GST Turnover:        %14s                              ║", formatAmount(profile.getIncome().getGstTurnover())));
        log.info(String.format("║ Other Income:        %14s                              ║", formatAmount(profile.getIncome().getOther())));
        log.info("║──────────────────────────────────────────────────────────────────║");
        log.info(String.format("║ TOTAL GROSS INCOME:  %14s                              ║", formatAmount(profile.computeTotalGrossIncome())));
        log.info("║──────────────────────────────────────────────────────────────────║");
        log.info(String.format("║ TDS Deducted:        %14s                              ║", formatAmount(profile.getTaxes().getTotalTds())));
        log.info(String.format("║ Advance Tax:         %14s                              ║", formatAmount(profile.getTaxes().getAdvanceTax())));
        log.info(String.format("║ Self-Assessment:     %14s                              ║", formatAmount(profile.getTaxes().getSelfAssessmentTax())));
        log.info(String.format("║ Refund:              %14s                              ║", formatAmount(profile.getTaxes().getRefund())));
        log.info("║──────────────────────────────────────────────────────────────────║");
        log.info(String.format("║ ITR Form:            %-44s║", itrForm));
        log.info(String.format("║ Complexity:          %-44s║", complexity.getScore() + "/10 (" + complexity.getLevel() + ")"));
        log.info(String.format("║ Quoted Fee:          INR %-40s║", pricing.getRecommendedFee()));
        log.info(String.format("║ Fields Extracted:    %-44s║", fieldCount));
        log.info(String.format("║ Confidence:          %-44s║", profile.getValidation().getConfidenceScore() + "%"));
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        
        if (!crossWarnings.isEmpty()) {
            log.info("║ ⚠ WARNINGS (Review Required):                                    ║");
            for (String w : crossWarnings) {
                log.info(String.format("║ - %-63s║", w.length() > 63 ? w.substring(0, 60) + "..." : w));
            }
        } else {
            log.info("║ ✅ All Cross-Validation Checks Passed                             ║");
        }
        log.info("╚══════════════════════════════════════════════════════════════════╝");
    }

    private String formatAmount(double amount) {
        if (amount == 0) return "0";
        return String.format("%,.0f", amount);
    }
}
