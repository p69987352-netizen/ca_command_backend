package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.services.AdminTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AdminTicketService adminTicketService;

    public PaymentController(AdminTicketService adminTicketService) {
        this.adminTicketService = adminTicketService;
    }

    // SIMULATING: Razorpay sending us a success webhook
    @PostMapping("/success/{ticketId}")
    public ResponseEntity<String> handlePaymentSuccess(@PathVariable UUID ticketId) {
        try {
            Ticket paidTicket = adminTicketService.markPaymentSuccessful(ticketId);
            return ResponseEntity.ok("Payment confirmed! Ticket " + paidTicket.getId() + " is now IN_PROGRESS.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing payment: " + e.getMessage());
        }
    }
}