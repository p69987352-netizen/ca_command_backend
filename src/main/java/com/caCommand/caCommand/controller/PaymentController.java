package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.services.AdminTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AdminTicketService adminTicketService;

    public PaymentController(AdminTicketService adminTicketService) {
        this.adminTicketService = adminTicketService;
    }

    @PostMapping("/success/{ticketId}")
    public ResponseEntity<String> handlePaymentSuccess(@PathVariable String ticketId) {
        Ticket paidTicket = adminTicketService.markPaymentSuccessful(ticketId);
        return ResponseEntity.ok("Payment confirmed! Ticket " + paidTicket.getId() + " is now IN_PROGRESS.");
    }

    @PostMapping("/{ticketId}/verify")
    public ResponseEntity<String> verifyPayment(@PathVariable String ticketId) {
        Ticket paidTicket = adminTicketService.markPaymentSuccessful(ticketId);
        return ResponseEntity.ok("Payment verified and approved!");
    }

    @PostMapping("/{ticketId}/reject")
    public ResponseEntity<String> rejectPayment(@PathVariable String ticketId) {
        adminTicketService.rejectPaymentProof(ticketId);
        return ResponseEntity.ok("Payment rejected.");
    }
}
