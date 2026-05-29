package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dtos.FinalDeliveryRequest;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.services.AdminTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final AdminTicketService adminTicketService;

    public StaffController(AdminTicketService adminTicketService) {
        this.adminTicketService = adminTicketService;
    }

    // API to see pending work for staff
    @GetMapping("/tickets/work-queue")
    public ResponseEntity<List<Ticket>> getWorkQueue() {
        return ResponseEntity.ok(adminTicketService.getInProgressTickets());
    }

    // API to deliver final work to client via WhatsApp
    @PostMapping("/tickets/{ticketId}/deliver")
    public ResponseEntity<Ticket> deliverFinalWork(
            @PathVariable UUID ticketId,
            @RequestBody FinalDeliveryRequest request) {

        Ticket finishedTicket = adminTicketService.completeTicketAndDeliver(ticketId, request.getFinalDocumentUrl(), request.getClosingMessage());
        return ResponseEntity.ok(finishedTicket);
    }

    @PostMapping("/tickets/{ticketId}/submit-qc")
    public ResponseEntity<Ticket> submitForQC(
            @PathVariable UUID ticketId,
            @RequestBody String documentUrl) { // Abhi simple string le rahe hain

        Ticket submittedTicket = adminTicketService.submitWorkForQC(ticketId, documentUrl);
        return ResponseEntity.ok(submittedTicket);
    }
}