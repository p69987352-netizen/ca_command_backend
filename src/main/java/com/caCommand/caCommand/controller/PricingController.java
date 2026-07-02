package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dto.PricingAnalysisDto;
import com.caCommand.caCommand.entities.ExtractedData;
import com.caCommand.caCommand.models.TaxProfile;
import com.caCommand.caCommand.repositories.ExtractedDataRepository;
import com.caCommand.caCommand.services.pricing.PricingEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@Slf4j
public class PricingController {

    private final ExtractedDataRepository extractedDataRepository;
    private final com.caCommand.caCommand.repositories.TicketRepository ticketRepository;
    private final PricingEngineService pricingEngineService;

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<PricingAnalysisDto> getPricingForTicket(@PathVariable String ticketId) {
        java.util.Optional<com.caCommand.caCommand.entities.Ticket> ticketOpt = ticketRepository.findById(java.util.UUID.fromString(ticketId));
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Optional<ExtractedData> dataOpt = extractedDataRepository.findFirstByClientIdOrderByCreatedAtDesc(ticketOpt.get().getClient().getId());
        if (dataOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExtractedData ed = dataOpt.get();
        if (ed.getRawJson() == null || ed.getRawJson().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
            TaxProfile profile = objectMapper.readValue(ed.getRawJson(), TaxProfile.class);
            PricingAnalysisDto dto = pricingEngineService.calculateFromProfile(profile, ed.getSuggestedItr());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Failed to parse TaxProfile from DB for pricing: {}", e.getMessage());
            // fallback
            PricingAnalysisDto fallback = new PricingAnalysisDto();
            fallback.setRecommendedFee(1500.0);
            return ResponseEntity.ok(fallback);
        }
    }
}
