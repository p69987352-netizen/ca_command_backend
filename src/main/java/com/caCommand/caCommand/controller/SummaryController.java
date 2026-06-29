package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dtos.SummaryResponseDto;
import com.caCommand.caCommand.entities.ActivityLog;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.ExtractedData;
import com.caCommand.caCommand.entities.UploadedDocument;
import com.caCommand.caCommand.repositories.ActivityLogRepository;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.ExtractedDataRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.repositories.UploadedDocumentRepository;
import com.caCommand.caCommand.repositories.AIAnalysisRepository;
import com.caCommand.caCommand.repositories.PaymentHistoryRepository;
import com.caCommand.caCommand.repositories.ClientHistoryRepository;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.entities.AIAnalysis;
import com.caCommand.caCommand.entities.PaymentHistory;
import com.caCommand.caCommand.entities.ClientHistory;
import com.caCommand.caCommand.services.GeminiService;
import com.caCommand.caCommand.services.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class SummaryController {

    private final ClientRepository clientRepository;
    private final ExtractedDataRepository extractedDataRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final TicketRepository ticketRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ClientHistoryRepository clientHistoryRepository;
    private final S3StorageService s3StorageService;

    @GetMapping("/{id}/summary")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<SummaryResponseDto> getClientSummary(@PathVariable UUID id) {
        Client client = clientRepository.findById(id).orElse(null);
        if (client == null) return ResponseEntity.notFound().build();

        // Use optimal query instead of findAll()
        ExtractedData data = extractedDataRepository.findFirstByClientIdOrderByCreatedAtDesc(id).orElse(null);

        List<Ticket> tickets = ticketRepository.findAllByClientIdOrderByCreatedAtDesc(id);

        List<SummaryResponseDto.DocumentDto> receivedDocs = new java.util.ArrayList<>();

        if (!tickets.isEmpty()) {
            String clientDocsStr = tickets.get(0).getClientDocuments();
            if (clientDocsStr != null && !clientDocsStr.isEmpty()) {
                String[] lines = clientDocsStr.split("\n");
                for (String line : lines) {
                    String[] parts = line.split(" :: ");
                    if (parts.length == 2) {
                        receivedDocs.add(new SummaryResponseDto.DocumentDto(parts[0].trim(), s3StorageService.getSignedUrl(parts[1].trim())));
                    } else if (parts.length > 0) {
                        receivedDocs.add(new SummaryResponseDto.DocumentDto(parts[0].trim(), ""));
                    }
                }
            }
        }

        String serviceType = tickets.isEmpty() ? "ITR Filing" : tickets.get(0).getServiceType();
        List<String> requiredDocs = GeminiService.SERVICE_DOCUMENTS.getOrDefault(serviceType, GeminiService.SERVICE_DOCUMENTS.get("ITR Filing"));
        if (requiredDocs == null) requiredDocs = List.of("PAN Card", "Aadhar Card"); // Fallback
        
        List<String> receivedDocNames = receivedDocs.stream()
                .map(SummaryResponseDto.DocumentDto::getName)
                .toList();
        
        List<String> missingDocs = requiredDocs.stream()
                .filter(doc -> !receivedDocNames.contains(doc))
                .toList();
        
        int completion = requiredDocs.isEmpty() ? 100 : (int) (((requiredDocs.size() - missingDocs.size()) * 100.0) / requiredDocs.size());

        // Readiness Score Calculation
        int readinessScore = completion;
        if (data != null && "High".equalsIgnoreCase(data.getRiskScore())) {
            readinessScore -= 20; // Penalize for high risk
        }

        List<ActivityLog> timeline = activityLogRepository.findByClientIdOrderByCreatedAtDesc(id);

        SummaryResponseDto response = new SummaryResponseDto();
        response.setClientProfile(client);
        response.setExtractedData(data);
        response.setReadinessScore(Math.max(0, readinessScore));
        response.setMissingDocuments(missingDocs);
        response.setReceivedDocuments(receivedDocs);
        response.setActivityTimeline(timeline);
        
        if (data != null && data.getNoticeType() != null && !data.getNoticeType().equals("None")) {
            response.setRecommendedAction("File Appeal for " + data.getNoticeType());
        } else if (data != null && data.getSuggestedItr() != null && !data.getSuggestedItr().isEmpty()) {
            response.setRecommendedAction("Proceed with " + data.getSuggestedItr() + " Filing");
        } else {
            response.setRecommendedAction("Proceed with ITR-1 Filing");
        }

        if (!tickets.isEmpty()) {
            Ticket latestTicket = tickets.get(0);
            response.setLatestTicketId(latestTicket.getId().toString());
            response.setStatus(latestTicket.getStatus());
            response.setFeeQuoted(latestTicket.getQuotedFee());
            response.setAdminFinalFee(latestTicket.getAdminFinalFee());
            
            if (latestTicket.getPaymentProofUrl() != null) {
                if (latestTicket.getPaymentProofUrl().startsWith("TEXT: ")) {
                    response.setPaymentProofUrl(latestTicket.getPaymentProofUrl());
                } else {
                    response.setPaymentProofUrl(s3StorageService.getSignedUrl(latestTicket.getPaymentProofUrl()));
                }
            }
            
            AIAnalysis aiAnalysis = aiAnalysisRepository.findFirstByTicketIdOrderByCreatedAtDesc(latestTicket.getId()).orElse(null);
            response.setAiAnalysis(aiAnalysis);
        }

        List<PaymentHistory> paymentHistory = paymentHistoryRepository.findByClientIdOrderByCreatedAtDesc(id);
        response.setPaymentHistory(paymentHistory);

        List<ClientHistory> previousCases = clientHistoryRepository.findByClientIdOrderByCompletionDateDesc(id);
        response.setPreviousCases(previousCases);
        
        // Populate Customer Intelligence Fields
        if (client.getTotalRevenueGenerated() != null) {
            response.setLifetimeRevenue(client.getTotalRevenueGenerated());
        } else {
            response.setLifetimeRevenue(paymentHistory.stream()
                .filter(p -> "PAID".equalsIgnoreCase(p.getStatus()))
                .mapToDouble(PaymentHistory::getAmount)
                .sum());
        }
        
        response.setTotalPreviousCases(client.getTotalCases() != null ? client.getTotalCases() : tickets.size());
        
        if (!tickets.isEmpty()) {
            response.setLastServiceName(tickets.get(0).getServiceType());
        }
        
        if (client.getLastServiceDate() != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy");
            response.setLastInteractionDate(client.getLastServiceDate().format(formatter));
        } else if (!tickets.isEmpty() && tickets.get(0).getCreatedAt() != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy");
            response.setLastInteractionDate(tickets.get(0).getCreatedAt().format(formatter));
        }

        return ResponseEntity.ok(response);
    }
}
