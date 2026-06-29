package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.entities.PricingAnalysis;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.entities.UploadedDocument;
import com.caCommand.caCommand.repositories.PricingAnalysisRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.repositories.UploadedDocumentRepository;
import com.caCommand.caCommand.services.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    private final TicketRepository ticketRepository;
    private final PricingAnalysisRepository pricingAnalysisRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;

    @GetMapping("/{ticketId}")
    public ResponseEntity<Map<String, Object>> getTicketIntelligence(@PathVariable String ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(UUID.fromString(ticketId));
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Ticket ticket = ticketOpt.get();
        Map<String, Object> response = new HashMap<>();
        
        // 1. Readiness Score
        response.put("readinessScore", ticket.getReadinessScore() != null ? ticket.getReadinessScore() : 0);

        // 2. Pricing Analysis
        if (ticket.getPricingAnalysisId() != null) {
            Optional<PricingAnalysis> pricingOpt = pricingAnalysisRepository.findById(ticket.getPricingAnalysisId());
            pricingOpt.ifPresent(pricing -> response.put("pricing", pricing));
        }

        // 3. Document Intelligence
        List<String> requiredDocs = GeminiService.SERVICE_DOCUMENTS.getOrDefault(ticket.getServiceType(), new ArrayList<>());
        List<UploadedDocument> uploads = uploadedDocumentRepository.findByClientId(ticket.getClient().getId());
        
        Set<String> receivedTypes = uploads.stream()
                .map(UploadedDocument::getDocumentType)
                .collect(Collectors.toSet());

        List<String> missingDocs = requiredDocs.stream()
                .filter(doc -> !receivedTypes.contains(doc))
                .collect(Collectors.toList());

        Map<String, Object> docsIntell = new HashMap<>();
        docsIntell.put("required", requiredDocs);
        docsIntell.put("received", receivedTypes);
        docsIntell.put("missing", missingDocs);
        docsIntell.put("optional", Arrays.asList("Investment Proofs", "Previous Returns"));

        response.put("documentIntelligence", docsIntell);

        return ResponseEntity.ok(response);
    }
}
