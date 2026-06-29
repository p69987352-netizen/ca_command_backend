package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.AIAnalysis;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.AIAnalysisRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TicketRepository ticketRepository;
    private final ClientRepository clientRepository;
    private final AIAnalysisRepository aiAnalysisRepository;

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        List<Ticket> allTickets = ticketRepository.findAll();
        List<Client> allClients = clientRepository.findAll();
        List<AIAnalysis> allAnalysis = aiAnalysisRepository.findAll();

        long totalClients = allClients.size();
        long pendingDocs = allTickets.stream().filter(t -> "DOCUMENT_PENDING".equals(t.getStatus())).count();
        long pendingApproval = allTickets.stream().filter(t -> "PENDING_ADMIN_APPROVAL".equals(t.getStatus())).count();
        long qcReview = allTickets.stream().filter(t -> "PENDING_ADMIN_QC".equals(t.getStatus())).count();
        long completed = allTickets.stream().filter(t -> "FINISHED".equals(t.getStatus()) || "COMPLETED".equals(t.getStatus())).count();
        
        long highRiskCases = allAnalysis.stream().filter(a -> "High".equalsIgnoreCase(a.getRiskScore())).count();
        
        double revenueCollected = allTickets.stream()
                .filter(t -> "PAID".equals(t.getPaymentStatus()))
                .mapToDouble(t -> t.getQuotedFee() != null ? t.getQuotedFee() : 0.0)
                .sum();
                
        double potentialRefunds = allAnalysis.stream()
                .mapToDouble(a -> a.getRefundOpportunity() != null ? a.getRefundOpportunity() : 0.0)
                .sum();

        analytics.put("totalClients", totalClients);
        analytics.put("pendingDocs", pendingDocs);
        analytics.put("caReviewsPending", pendingApproval + qcReview);
        analytics.put("readyForFiling", completed);
        analytics.put("highRiskCases", highRiskCases);
        analytics.put("revenueCollected", revenueCollected);
        analytics.put("potentialRefunds", potentialRefunds);

        return ResponseEntity.ok(analytics);
    }
}
