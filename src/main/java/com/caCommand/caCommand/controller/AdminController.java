package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dtos.AdminApprovalRequest;
import com.caCommand.caCommand.dtos.StaffRequest;
import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.services.AdminTicketService;
import com.caCommand.caCommand.services.StaffService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminTicketService adminTicketService;
    private final StaffService staffService;
    private final WhatsAppMessageSender whatsappMessageSender;

    public AdminController(
            AdminTicketService adminTicketService,
            StaffService staffService,
            WhatsAppMessageSender whatsappMessageSender
    ) {
        this.adminTicketService = adminTicketService;
        this.staffService = staffService;
        this.whatsappMessageSender = whatsappMessageSender;
    }

    // =====================================================
    // GET ALL TICKETS
    // =====================================================
    @GetMapping("/tickets/all")
    public ResponseEntity<List<Ticket>> getAllTickets() {

        return ResponseEntity.ok(
                adminTicketService.getAllTickets()
        );
    }

    // =====================================================
    // GET PENDING TICKETS
    // =====================================================
    @GetMapping("/tickets/pending")
    public ResponseEntity<List<Ticket>> getPendingTickets() {

        return ResponseEntity.ok(
                adminTicketService.getPendingTickets()
        );
    }

    // =====================================================
    // APPROVE TICKET + SET FEE
    // =====================================================
    @PostMapping("/tickets/{ticketId}/approve")
    public ResponseEntity<Ticket> approveTicket(
            @PathVariable UUID ticketId,
            @RequestBody AdminApprovalRequest approvalRequest
    ) {

        return ResponseEntity.ok(
                adminTicketService.approveTicketAndSetFee(
                        ticketId,
                        approvalRequest
                )
        );
    }

    // =====================================================
    // MOCK PAYMENT SUCCESS
    // =====================================================
    @PostMapping("/tickets/{ticketId}/mock-pay")
    public ResponseEntity<Ticket> simulatePayment(
            @PathVariable UUID ticketId
    ) {

        return ResponseEntity.ok(
                adminTicketService.markPaymentSuccessful(ticketId)
        );
    }

    // =====================================================
    // ASSIGN TICKET TO STAFF
    // =====================================================
    @PostMapping("/tickets/{ticketId}/assign/{staffId}")
    public ResponseEntity<Ticket> assignTicketToStaff(

            @PathVariable UUID ticketId,
            @PathVariable UUID staffId,
            @RequestBody Map<String, String> payload

    ) {

        String priority =
                payload.getOrDefault("priority", "Normal");

        String notes =
                payload.getOrDefault("notes", "");

        Ticket assignedTicket =
                adminTicketService.assignToStaff(
                        ticketId,
                        staffId,
                        priority,
                        notes
                );

        return ResponseEntity.ok(assignedTicket);
    }

    // =====================================================
    // ADD STAFF
    // =====================================================
    @PostMapping("/staff")
    public ResponseEntity<Staff> addStaff(
            @RequestBody StaffRequest staffRequest
    ) {

        Staff newStaff = staffService.addStaffMember(
                staffRequest.getName(),
                staffRequest.getPhoneNumber()
        );

        // WhatsApp Welcome Message
        String welcomeMsg =
                "🎉 *WELCOME TO CA COMMAND CENTER*\n\n" +
                        "Hi *" + newStaff.getName() + "*," +
                        " you have been successfully added as a Staff Member ✅\n\n" +
                        "All assigned tasks will come directly here.";

        whatsappMessageSender.sendMessage(
                newStaff.getPhoneNumber(),
                welcomeMsg
        );

        return ResponseEntity.ok(newStaff);
    }

    // =====================================================
    // GET ALL STAFF
    // =====================================================
    @GetMapping("/staff")
    public ResponseEntity<List<Staff>> getAllStaff() {

        return ResponseEntity.ok(
                staffService.getAllStaff()
        );
    }

    // =====================================================
    // REMOVE STAFF
    // =====================================================
    @DeleteMapping("/staff/{staffId}")
    public ResponseEntity<String> removeStaff(
            @PathVariable UUID staffId
    ) {

        adminTicketService.removeStaff(staffId);

        return ResponseEntity.ok(
                "Staff removed successfully"
        );
    }

    // =====================================================
    // SUBMIT WORK FOR QC
    // =====================================================
    @PostMapping("/tickets/{ticketId}/submit-qc")
    public ResponseEntity<Ticket> submitForQC(

            @PathVariable UUID ticketId,
            @RequestBody String documentUrl

    ) {

        return ResponseEntity.ok(
                adminTicketService.submitWorkForQC(
                        ticketId,
                        documentUrl
                )
        );
    }

    // =====================================================
    // FINAL DELIVERY TO CLIENT
    // =====================================================
    @PostMapping("/tickets/{ticketId}/deliver")
    public ResponseEntity<Ticket> deliverToClient(

            @PathVariable UUID ticketId,
            @RequestBody(required = false) String closingMessage

    ) {

        return ResponseEntity.ok(
                adminTicketService.approveAndDeliver(
                        ticketId,
                        closingMessage
                )
        );
    }

    // =====================================================
    // DASHBOARD STATS
    // =====================================================
    @GetMapping("/tickets/stats")
    public ResponseEntity<Map<String, Long>> getTicketStats() {

        return ResponseEntity.ok(
                adminTicketService.getTicketStatistics()
        );
    }
}