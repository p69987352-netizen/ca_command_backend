package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.PricingAnalysis;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.ExtractedData;
import com.caCommand.caCommand.entities.UploadedDocument;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.entities.AIAnalysis;
import com.caCommand.caCommand.repositories.ExtractedDataRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.repositories.UploadedDocumentRepository;
import com.caCommand.caCommand.repositories.ActivityLogRepository;
import com.caCommand.caCommand.repositories.AIAnalysisRepository;
import com.caCommand.caCommand.services.whatsapp.WhatsAppProvider;
import com.caCommand.caCommand.entities.ActivityLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyDocumentExtractionService {

    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final ExtractedDataRepository extractedDataRepository;
    private final TicketRepository ticketRepository;
    private final com.caCommand.caCommand.repositories.ClientRepository clientRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final WhatsAppProvider whatsAppProvider;
    private final GeminiService geminiService;
    private final LegacyPricingEngineService pricingEngineService;

    private void logActivity(Client client, String eventType, String message) {
        ActivityLog logEntry = new ActivityLog();
        logEntry.setClient(client);
        logEntry.setEventType(eventType);
        logEntry.setMessage(message);
        activityLogRepository.save(logEntry);
    }

    @Async
    @Transactional
    public void processUploadedDocument(java.util.UUID clientId, String fileName, String documentUrl, String documentType) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new RuntimeException("Client not found"));
        // 1. Save UploadedDocument
        UploadedDocument doc = new UploadedDocument();
        doc.setClient(client);
        doc.setFileName(fileName);
        doc.setDocumentType(documentType);
        doc.setStoragePath(documentUrl);
        uploadedDocumentRepository.save(doc);
        
        logActivity(client, "UPLOAD", "Client uploaded document: " + documentType);

        runAiExtractionPipeline(client, documentType, documentUrl);
    }

    private void runAiExtractionPipeline(Client client, String documentType, String documentUrl) {
        log.info("Starting real Arjun AI Extraction Pipeline for client: {}", client.getName());
        logActivity(client, "AI_ANALYSIS_START", "Arjun AI started analyzing " + documentType);
        
        log.info("Running Arjun AI LLM Analysis via Vision Model...");
        
        String pdfPassword = null;
        if (client.getPan() != null && client.getDob() != null) {
            String dobDigits = client.getDob().replaceAll("[^0-9]", "");
            pdfPassword = client.getPan().toLowerCase() + dobDigits;
        }
        
        java.util.Map<String, Object> extractedFields = geminiService.extractFinancialData(documentUrl, documentType, pdfPassword);

        ExtractedData data = extractedDataRepository.findFirstByClientIdOrderByCreatedAtDesc(client.getId()).orElse(new ExtractedData());
        data.setClient(client);
        data.setDocumentType(documentType);
        data.setAssessmentYear("2024-25");
        data.setFinancialYear("2023-24");
        data.setRawJson(extractedFields.toString());

        if (!extractedFields.isEmpty()) {
            data.setSalaryIncome(mergeValue(data.getSalaryIncome(), extractedFields.get("salaryIncome")));
            data.setInterestIncome(mergeValue(data.getInterestIncome(), extractedFields.get("interestIncome")));
            data.setDividendIncome(mergeValue(data.getDividendIncome(), extractedFields.get("dividendIncome")));
            data.setCapitalGains(mergeValue(data.getCapitalGains(), extractedFields.get("capitalGains")));
            data.setTds(mergeValue(data.getTds(), extractedFields.get("tds")));
            
            Double aisIncome = mergeValue(data.getAisReportedIncome(), extractedFields.get("aisReportedIncome"));
            Double tisIncome = mergeValue(data.getTisReportedIncome(), extractedFields.get("tisReportedIncome"));
            data.setAisReportedIncome(aisIncome);
            data.setTisReportedIncome(tisIncome);
            
            if (aisIncome != null && tisIncome != null && aisIncome > 0) {
                data.setIncomeDifference(Math.abs(aisIncome - tisIncome));
            } else {
                data.setIncomeDifference(0.0);
            }
            
            data.setRefundOpportunity(mergeValue(data.getRefundOpportunity(), extractedFields.get("refundOpportunity")));
            data.setDemandOutstanding(mergeValue(data.getDemandOutstanding(), extractedFields.get("demandOutstanding")));
            
            if (extractedFields.get("noticeType") != null && !"None".equals(extractedFields.get("noticeType"))) {
                data.setNoticeType((String) extractedFields.get("noticeType"));
            } else if (data.getNoticeType() == null) {
                data.setNoticeType("None");
            }
            
            if (extractedFields.get("suggestedItr") != null && !"ITR-1".equals(extractedFields.get("suggestedItr"))) {
                data.setSuggestedItr((String) extractedFields.get("suggestedItr"));
            } else if (data.getSuggestedItr() == null) {
                data.setSuggestedItr("ITR-1");
            }
            
            if (extractedFields.get("riskScore") != null && !"Low".equals(extractedFields.get("riskScore"))) {
                data.setRiskScore((String) extractedFields.get("riskScore"));
            } else if (data.getRiskScore() == null) {
                data.setRiskScore("Low");
            }
            
            // Calculate and update Client Income Range
            double totalIncome = (data.getSalaryIncome() != null ? data.getSalaryIncome() : 0.0)
                    + (data.getInterestIncome() != null ? data.getInterestIncome() : 0.0)
                    + (data.getDividendIncome() != null ? data.getDividendIncome() : 0.0)
                    + (data.getCapitalGains() != null ? data.getCapitalGains() : 0.0);
            if (aisIncome != null && aisIncome > totalIncome) {
                totalIncome = aisIncome;
            }
            if (totalIncome < 500000) {
                client.setIncomeRange("BELOW_5L");
            } else if (totalIncome <= 1000000) {
                client.setIncomeRange("5L_TO_10L");
            } else {
                client.setIncomeRange("ABOVE_10L");
            }
            clientRepository.save(client);
            
        } else {
            // Fallback if AI fails completely (rare)
            data.setRiskScore("Medium");
            data.setNoticeType("None");
        }

        extractedDataRepository.save(data);

        // Fetch the latest ticket right before updating to prevent race conditions (Lost Update Anomaly)
        List<Ticket> tickets = ticketRepository.findAllByClientIdOrderByCreatedAtDesc(client.getId());
        if (!tickets.isEmpty()) {
            Ticket ticket = tickets.get(0);
            AIAnalysis aiAnalysis = aiAnalysisRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticket.getId()).orElse(new AIAnalysis());
            aiAnalysis.setTicket(ticket);
            
            int complexity = calculateComplexity(data);
            aiAnalysis.setComplexityScore(complexity);
            
            int readiness = calculateReadinessScore(extractedFields);
            aiAnalysis.setReadinessScore(readiness);
            
            aiAnalysis.setRiskScore(data.getRiskScore());
            aiAnalysis.setRefundOpportunity(data.getRefundOpportunity());
            
            aiAnalysis.setAiConfidenceScore(85); // Dummy for now
            aiAnalysis.setRecommendation("Proceed with CA Review");
            
            // Calculate Pricing
            double recommendedFee = pricingEngineService.calculateFee(ticket.getTicketCategory(), complexity);
            aiAnalysis.setFeeRecommendation(String.valueOf(recommendedFee));
            
            aiAnalysis.setSummary("AI extracted data successfully. Discrepancies noted: " + data.getIncomeDifference());
            
            aiAnalysisRepository.save(aiAnalysis);
            
            // Update Ticket
            ticket.setReadinessScore(readiness);
            ticket.setQuotedFee(recommendedFee);
            ticketRepository.save(ticket);
        }

        log.info("AI Analysis and Pricing Generation Complete for {}", client.getName());
        logActivity(client, "RISK_SCORE", "Arjun AI generated Risk Score: " + data.getRiskScore());
    }

    private int calculateComplexity(ExtractedData data) {
        int score = 20; // Base
        if (data.getIncomeDifference() != null && data.getIncomeDifference() > 10000) score += 40;
        if (data.getDividendIncome() != null && data.getDividendIncome() > 0) score += 10;
        if (data.getNoticeType() != null && !"None".equals(data.getNoticeType())) score += 50;
        return Math.min(score, 100);
    }

    private int calculateReadinessScore(java.util.Map<String, Object> fields) {
        if (fields.isEmpty()) return 30;
        int score = 50;
        if (fields.get("salaryIncome") != null && ((Double)fields.get("salaryIncome") > 0)) score += 20;
        if (fields.get("capitalGains") != null && ((Double)fields.get("capitalGains") > 0)) score += 10;
        if (fields.get("tds") != null && ((Double)fields.get("tds") > 0)) score += 15;
        if (fields.get("noticeType") != null && !"None".equals(fields.get("noticeType"))) score += 15;
        return Math.min(score, 100);
    }
    
    private Double mergeValue(Double existingValue, Object newValueObj) {
        Double newValue = newValueObj instanceof Double ? (Double) newValueObj : 
                         (newValueObj instanceof Integer ? ((Integer) newValueObj).doubleValue() : null);
        if (newValue != null && newValue > 0) {
            return newValue;
        }
        return existingValue != null ? existingValue : 0.0;
    }
}
