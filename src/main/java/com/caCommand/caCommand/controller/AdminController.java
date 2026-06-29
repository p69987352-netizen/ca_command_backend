package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.dtos.AdminApprovalRequest;
import com.caCommand.caCommand.dtos.AssignTicketRequest;
import com.caCommand.caCommand.dtos.DeadlineRequest;
import com.caCommand.caCommand.dtos.NoteRequest;
import com.caCommand.caCommand.dtos.StaffRequest;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.ServicePricing;
import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.services.AdminTicketService;
import com.caCommand.caCommand.services.PricingService;
import com.caCommand.caCommand.services.StaffService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminTicketService adminTicketService;
    private final StaffService staffService;
    private final WhatsAppMessageSender whatsappMessageSender;
    private final PricingService pricingService;
    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;
    private final com.caCommand.caCommand.repositories.CustomDocumentRequestRepository customDocumentRequestRepository;

    public AdminController(
            AdminTicketService adminTicketService,
            StaffService staffService,
            WhatsAppMessageSender whatsappMessageSender,
            PricingService pricingService,
            ClientRepository clientRepository,
            TicketRepository ticketRepository,
            com.caCommand.caCommand.repositories.CustomDocumentRequestRepository customDocumentRequestRepository
    ) {
        this.adminTicketService = adminTicketService;
        this.staffService = staffService;
        this.whatsappMessageSender = whatsappMessageSender;
        this.pricingService = pricingService;
        this.clientRepository = clientRepository;
        this.ticketRepository = ticketRepository;
        this.customDocumentRequestRepository = customDocumentRequestRepository;
    }

    // ======================================================
    // TICKETS
    // ======================================================
    
    @GetMapping("/tickets/{ticketId}/file-url")
    public ResponseEntity<Map<String, String>> getPresignedFileUrl(@PathVariable String ticketId) {
        String url = adminTicketService.getPresignedDocumentUrl(ticketId);
        return ResponseEntity.ok(Map.of("url", url != null ? url : ""));
    }

    @GetMapping("/tickets/all")
    public ResponseEntity<List<Ticket>> getAllTickets() {
        return ResponseEntity.ok(adminTicketService.getAllTickets());
    }

    @PostMapping("/tickets/{ticketId}/remind")
    public ResponseEntity<String> sendReminder(
            @PathVariable String ticketId,
            @RequestBody(required = false) String customMessage
    ) {
        adminTicketService.sendReminderToClient(ticketId, customMessage);
        return ResponseEntity.ok("Reminder sent successfully.");
    }

    public record CustomDocumentPayload(String documentName, String message) {}

    @PostMapping("/tickets/{ticketId}/request-document")
    public ResponseEntity<com.caCommand.caCommand.entities.CustomDocumentRequest> requestCustomDocument(
            @PathVariable String ticketId,
            @RequestBody CustomDocumentPayload payload
    ) {
        Ticket ticket = ticketRepository.findById(java.util.UUID.fromString(ticketId)).orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        com.caCommand.caCommand.entities.CustomDocumentRequest request = new com.caCommand.caCommand.entities.CustomDocumentRequest();
        request.setTicket(ticket);
        request.setDocumentName(payload.documentName());
        request.setMessage(payload.message());
        request.setStatus("PENDING");
        request.setRequestedBy("ADMIN");
        
        customDocumentRequestRepository.save(request);
        
        ticket.setStatus(com.caCommand.caCommand.enums.TicketStatus.WAITING_FOR_CLIENT_DOCUMENT.name());
        ticketRepository.save(ticket);
        
        String waMessage = String.format("📄 *Additional document requested by your CA team*\n\nDocument: %s\n%s\n\nPlease upload the document when available.", 
            payload.documentName(), 
            payload.message() != null ? "\nMessage: " + payload.message() : "");
            
        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), waMessage);
        
        return ResponseEntity.ok(request);
    }

    @GetMapping("/tickets/pending")
    public ResponseEntity<List<Ticket>> getPendingTickets() {
        return ResponseEntity.ok(adminTicketService.getPendingTickets());
    }

    @PostMapping("/tickets/{ticketId}/approve")
    public ResponseEntity<Ticket> approveTicket(
            @PathVariable String ticketId,
            @Valid @RequestBody AdminApprovalRequest approvalRequest
    ) {
        return ResponseEntity.ok(adminTicketService.approveTicketAndSetFee(ticketId, approvalRequest));
    }

    @PostMapping("/tickets/{ticketId}/mock-pay")
    public ResponseEntity<Ticket> simulatePayment(@PathVariable String ticketId) {
        return ResponseEntity.ok(adminTicketService.markPaymentSuccessful(ticketId));
    }

    @PostMapping("/tickets/{ticketId}/assign/{staffId}")
    public ResponseEntity<Ticket> assignTicketToStaff(
            @PathVariable String ticketId,
            @PathVariable String staffId,
            @Valid @RequestBody(required = false) AssignTicketRequest payload
    ) {
        String priority = payload != null ? payload.priority() : "Normal";
        String notes = payload != null && payload.notes() != null ? payload.notes() : "";
        return ResponseEntity.ok(adminTicketService.assignToStaff(ticketId, staffId, priority, notes));
    }

    @PostMapping("/tickets/{ticketId}/submit-qc")
    public ResponseEntity<Ticket> submitForQC(
            @PathVariable String ticketId,
            @RequestBody String documentUrl
    ) {
        return ResponseEntity.ok(adminTicketService.submitWorkForQC(ticketId, documentUrl));
    }

    @PostMapping("/tickets/{ticketId}/deliver")
    public ResponseEntity<Ticket> deliverToClient(
            @PathVariable String ticketId,
            @RequestBody(required = false) String closingMessage
    ) {
        return ResponseEntity.ok(adminTicketService.approveAndDeliver(ticketId, closingMessage));
    }

    @PostMapping("/tickets/{ticketId}/request-changes")
    public ResponseEntity<Ticket> requestChanges(
            @PathVariable String ticketId,
            @RequestBody String changeRequest
    ) {
        return ResponseEntity.ok(adminTicketService.requestStaffChanges(ticketId, changeRequest));
    }

    @PostMapping("/tickets/{ticketId}/reassign/{staffId}")
    public ResponseEntity<Ticket> reassignTicket(
            @PathVariable String ticketId,
            @PathVariable String staffId,
            @RequestBody(required = false) String notes
    ) {
        return ResponseEntity.ok(adminTicketService.reassignToStaff(ticketId, staffId, notes));
    }

    @PostMapping("/tickets/{ticketId}/note")
    public ResponseEntity<Ticket> sendNote(
            @PathVariable String ticketId,
            @Valid @RequestBody NoteRequest request
    ) {
        return ResponseEntity.ok(adminTicketService.sendAdminNote(ticketId, request));
    }

    @PostMapping("/tickets/{ticketId}/deadline")
    public ResponseEntity<Ticket> setDeadline(
            @PathVariable String ticketId,
            @Valid @RequestBody DeadlineRequest request
    ) {
        return ResponseEntity.ok(adminTicketService.setDeadline(ticketId, request));
    }

    @PostMapping("/tickets/{ticketId}/ask-staff-update")
    public ResponseEntity<Ticket> askStaffUpdate(
            @PathVariable String ticketId,
            @RequestBody(required = false) String adminMessage
    ) {
        return ResponseEntity.ok(adminTicketService.askStaffForUpdate(ticketId, adminMessage));
    }

    @GetMapping("/tickets/stats")
    public ResponseEntity<Map<String, Long>> getTicketStats() {
        return ResponseEntity.ok(adminTicketService.getTicketStatistics());
    }

    @PostMapping("/tickets/{ticketId}/verify-payment")
    public ResponseEntity<Ticket> verifyPayment(@PathVariable String ticketId) {
        return ResponseEntity.ok(adminTicketService.verifyPayment(ticketId));
    }

    // ======================================================
    // 📄 PDF DOWNLOAD
    // ======================================================
    @GetMapping("/tickets/{ticketId}/download-pdf")
    public ResponseEntity<org.springframework.core.io.Resource> downloadPdf(@PathVariable String ticketId) {
        Ticket ticket = ticketRepository.findById(java.util.UUID.fromString(ticketId)).orElseThrow();
        if (ticket.getAisPdfPath() == null || ticket.getAisPdfPath().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(ticket.getAisPdfPath());
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ======================================================
    // 🔴 CREDENTIAL REQUEST SYSTEM
    // ======================================================
    @PostMapping("/tickets/{ticketId}/request-credentials")
    public ResponseEntity<Ticket> requestCredentials(
            @PathVariable String ticketId,
            @RequestBody(required = false) String credentialLabel
    ) {
        return ResponseEntity.ok(adminTicketService.requestCredentials(ticketId, credentialLabel));
    }

    // ======================================================
    // PAYMENTS
    // ======================================================
    @GetMapping("/payments/pending")
    public ResponseEntity<List<Ticket>> getPendingPayments() {
        return ResponseEntity.ok(adminTicketService.getAwaitingPaymentTickets());
    }

    @GetMapping("/payments/paid")
    public ResponseEntity<List<Ticket>> getPaidPayments() {
        return ResponseEntity.ok(adminTicketService.getPaidTickets());
    }

    // ======================================================
    // STAFF
    // ======================================================
    @PostMapping("/staff")
    public ResponseEntity<Staff> addStaff(@Valid @RequestBody StaffRequest staffRequest) {
        Staff newStaff = staffService.addStaffMember(staffRequest.getName(), staffRequest.getPhoneNumber());
        String welcomeMsg = "🎉 *WELCOME TO CA COMMAND CENTER*\n\n" +
                "Hi *" + newStaff.getName() + "*," +
                " you have been successfully added as a Staff Member ✅\n\n" +
                "All assigned tasks will come directly here on WhatsApp.\n\n" +
                "Commands:\nSTATUS — view current file\nREQUEST: [doc] — ask client\nQUERY: [question] — ask client\nUPDATE: [note] — save progress";
        whatsappMessageSender.sendMessage(newStaff.getPhoneNumber(), welcomeMsg);
        return ResponseEntity.ok(newStaff);
    }

    @GetMapping("/staff")
    public ResponseEntity<List<Staff>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @DeleteMapping("/staff/{staffId}")
    public ResponseEntity<String> removeStaff(@PathVariable String staffId) {
        adminTicketService.removeStaff(staffId);
        return ResponseEntity.ok("Staff removed successfully");
    }

    // ======================================================
    // 📜 CLIENT HISTORY
    // ======================================================
    @GetMapping("/clients/history")
    public ResponseEntity<Map<String, Object>> getClientHistory(@RequestParam String query) {
        return ResponseEntity.ok(adminTicketService.getClientHistory(query));
    }

    @GetMapping("/clients/search")
    public ResponseEntity<List<Client>> searchClients(@RequestParam String query) {
        List<Client> results = clientRepository.searchByPhoneOrName(query);
        return ResponseEntity.ok(results);
    }

    // ======================================================
    // 💰 PRICING ENGINE
    // ======================================================
    @GetMapping("/pricing/suggest")
    public ResponseEntity<Map<String, Object>> suggestPricing(
            @RequestParam String service,
            @RequestParam(required = false) String pinCode,
            @RequestParam(required = false) String income
    ) {
        PricingService.PricingResult result = pricingService.calculateFee(service, pinCode, income);
        if (result.hasError()) {
            return ResponseEntity.badRequest().body(Map.of("error", result.error()));
        }
        return ResponseEntity.ok(result.toMap());
    }

    @GetMapping("/pricing/all")
    public ResponseEntity<List<ServicePricing>> getAllPricing() {
        return ResponseEntity.ok(pricingService.getAllPricing());
    }

    @GetMapping("/pricing/pincode")
    public ResponseEntity<Map<String, Object>> getPinCodeInfo(@RequestParam String pinCode) {
        return pricingService.getPinCodeInfo(pinCode)
                .map(tier -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("pinCode", tier.getPinCode());
                    info.put("city", tier.getCity());
                    info.put("state", tier.getState());
                    info.put("tier", tier.getTier());
                    info.put("discountPercent", tier.getDiscountPercent());
                    info.put("tierLabel", tier.getTier().equals("A") ? "Metro (Full Rate)"
                            : tier.getTier().equals("B") ? "Medium City (" + tier.getDiscountPercent() + "% off)"
                            : "Small City (" + tier.getDiscountPercent() + "% off)");
                    return ResponseEntity.ok(info);
                })
                .orElse(ResponseEntity.ok(Map.of(
                        "pinCode", pinCode,
                        "tier", "A",
                        "discountPercent", 0,
                        "tierLabel", "Unknown PIN Code — Full Rate Applied",
                        "city", "Unknown"
                )));
    }

    // ======================================================
    // 📞 CALL-BASED SERVICE TICKETS
    // ======================================================
    @GetMapping("/tickets/call-pending")
    public ResponseEntity<List<Ticket>> getCallPendingTickets() {
        return ResponseEntity.ok(adminTicketService.getCallPendingTickets());
    }

    @PostMapping("/tickets/{id}/mark-call-done")
    public ResponseEntity<Ticket> markCallDone(
            @PathVariable String id,
            @RequestBody(required = false) String notes
    ) {
        return ResponseEntity.ok(adminTicketService.markCallDone(id, notes));
    }
}
