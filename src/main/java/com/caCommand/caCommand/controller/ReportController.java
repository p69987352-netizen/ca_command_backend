package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dtos.SummaryResponseDto;
import com.caCommand.caCommand.services.PdfGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final SummaryController summaryController;
    private final PdfGenerationService pdfGenerationService;

    @GetMapping("/generate/{clientId}")
    public ResponseEntity<byte[]> generateClientReport(@PathVariable UUID clientId) {
        ResponseEntity<SummaryResponseDto> summaryResponse = summaryController.getClientSummary(clientId);
        
        if (!summaryResponse.getStatusCode().is2xxSuccessful() || summaryResponse.getBody() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdfBytes = pdfGenerationService.generateClientReport(summaryResponse.getBody());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Client_Report_" + clientId + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
