package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dtos.StaffClientMessageRequest;
import com.caCommand.caCommand.dtos.StaffProgressUpdateRequest;
import com.caCommand.caCommand.dtos.FinalDeliveryRequest;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.services.AdminTicketService;
import jakarta.validation.Valid;
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

    @GetMapping("/{staffId}/tickets")
    public ResponseEntity<List<Ticket>> getStaffTickets(@PathVariable String staffId) {
        return ResponseEntity.ok(adminTicketService.getWorkQueueForStaff(staffId));
    }

    // API to deliver final work to client via WhatsApp
    @PostMapping("/tickets/{ticketId}/deliver")
    public ResponseEntity<Ticket> deliverFinalWork(
            @PathVariable String ticketId,
            @Valid @RequestBody FinalDeliveryRequest request) {

        Ticket finishedTicket = adminTicketService.completeTicketAndDeliver(ticketId, request.getFinalDocumentUrl(), request.getClosingMessage());
        return ResponseEntity.ok(finishedTicket);
    }

    @PostMapping("/tickets/{ticketId}/submit-qc")
    public ResponseEntity<Ticket> submitForQC(
            @PathVariable String ticketId,
            @RequestBody String documentUrl) { // Abhi simple string le rahe hain

        Ticket submittedTicket = adminTicketService.submitWorkForQC(ticketId, documentUrl);
        return ResponseEntity.ok(submittedTicket);
    }

    @PostMapping("/tickets/{ticketId}/client-message")
    public ResponseEntity<Ticket> messageClient(
            @PathVariable String ticketId,
            @Valid @RequestBody StaffClientMessageRequest request) {

        return ResponseEntity.ok(adminTicketService.staffMessageClient(ticketId, request));
    }

    @PostMapping("/tickets/{ticketId}/progress")
    public ResponseEntity<Ticket> updateProgress(
            @PathVariable String ticketId,
            @Valid @RequestBody StaffProgressUpdateRequest request) {

        return ResponseEntity.ok(adminTicketService.staffUpdateProgress(ticketId, request));
    }
}
