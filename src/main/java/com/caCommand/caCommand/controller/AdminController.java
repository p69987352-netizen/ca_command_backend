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

import com.caCommand.caCommand.services.S3StorageService;

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
    private final S3StorageService s3StorageService;

    public AdminController(
            AdminTicketService adminTicketService,
            StaffService staffService,
            WhatsAppMessageSender whatsappMessageSender,
            PricingService pricingService,
            ClientRepository clientRepository,
            TicketRepository ticketRepository,
            com.caCommand.caCommand.repositories.CustomDocumentRequestRepository customDocumentRequestRepository,
            S3StorageService s3StorageService
    ) {
        this.adminTicketService = adminTicketService;
        this.staffService = staffService;
        this.whatsappMessageSender = whatsappMessageSender;
        this.pricingService = pricingService;
        this.clientRepository = clientRepository;
        this.ticketRepository = ticketRepository;
        this.customDocumentRequestRepository = customDocumentRequestRepository;
        this.s3StorageService = s3StorageService;
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
        return ResponseEntity.ok(adminTicketService.assignToStaff(ticketId, staffId, payload));
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
    public ResponseEntity<?> downloadPdf(@PathVariable String ticketId) {
        Ticket ticket = ticketRepository.findById(java.util.UUID.fromString(ticketId)).orElseThrow();
        
        // 1. Try aisPdfPath
        if (ticket.getAisPdfPath() != null && !ticket.getAisPdfPath().isEmpty()) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(ticket.getAisPdfPath());
                org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                            .body(resource);
                }
            } catch (Exception e) {
                // fall through
            }
        }
        
        // 2. Try staffSubmittedDocument
        if (ticket.getStaffSubmittedDocument() != null && !ticket.getStaffSubmittedDocument().isEmpty()) {
            String url = ticket.getStaffSubmittedDocument();
            String signedUrl = s3StorageService.getSignedUrl(url);
            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                    .location(java.net.URI.create(signedUrl))
                    .build();
        }
        
        // 3. Try clientDocuments
        if (ticket.getClientDocuments() != null && !ticket.getClientDocuments().isEmpty()) {
            String docs = ticket.getClientDocuments();
            for (String line : docs.split("\n")) {
                if (line.contains("::")) {
                    String url = line.split("::")[1].trim();
                    String signedUrl = s3StorageService.getSignedUrl(url);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                            .location(java.net.URI.create(signedUrl))
                            .build();
                } else if (line.contains("http")) {
                    int start = line.indexOf("http");
                    String url = line.substring(start).trim();
                    String signedUrl = s3StorageService.getSignedUrl(url);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                            .location(java.net.URI.create(signedUrl))
                            .build();
                }
            }
        }
        
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("message", "No reports or documents have been uploaded for this ticket yet."));
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
        // Using custom 'staff_welcome_new' template
        whatsappMessageSender.sendTemplateMessage(
                newStaff.getPhoneNumber(),
                "staff_welcome_new",
                "en_us",
                java.util.List.of()
        );
        
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

    @GetMapping("/staff/{staffId}/performance")
    public ResponseEntity<com.caCommand.caCommand.dtos.StaffPerformanceDTO> getStaffPerformance(@PathVariable String staffId) {
        return ResponseEntity.ok(adminTicketService.getStaffPerformance(staffId));
    }

    @GetMapping("/staff/{staffId}/attendance")
    public ResponseEntity<List<com.caCommand.caCommand.entities.Attendance>> getStaffAttendance(@PathVariable String staffId) {
        return ResponseEntity.ok(adminTicketService.getStaffAttendance(staffId));
    }

    @GetMapping("/staff/{staffId}/tickets")
    public ResponseEntity<List<Ticket>> getStaffTickets(@PathVariable String staffId) {
        return ResponseEntity.ok(adminTicketService.getStaffTickets(staffId));
    }

    @GetMapping("/staff/attendance/today")
    public ResponseEntity<List<com.caCommand.caCommand.entities.Attendance>> getTodayAttendance() {
        return ResponseEntity.ok(adminTicketService.getTodayAttendance());
    }

    @GetMapping("/staff/attendance/date/{date}")
    public ResponseEntity<List<com.caCommand.caCommand.entities.Attendance>> getAttendanceByDate(@PathVariable String date) {
        return ResponseEntity.ok(adminTicketService.getAttendanceByDate(java.time.LocalDate.parse(date)));
    }

    @GetMapping("/staff/attendance/month/{year}/{month}")
    public ResponseEntity<List<com.caCommand.caCommand.entities.Attendance>> getAttendanceByMonth(@PathVariable int year, @PathVariable int month) {
        return ResponseEntity.ok(adminTicketService.getAttendanceByMonth(year, month));
    }

    @PostMapping("/staff/remind-attendance")
    public ResponseEntity<String> sendManualAttendanceReminders(@RequestBody(required = false) java.util.Map<String, java.util.List<String>> payload) {
        java.util.List<String> staffIds = payload != null ? payload.get("staffIds") : null;
        adminTicketService.sendManualAttendanceReminders(staffIds);
        return ResponseEntity.ok("Attendance reminders dispatched successfully.");
    }

    @PostMapping("/staff/generate-report")
    public ResponseEntity<String> generateAndSendAttendanceReport() {
        adminTicketService.generateAndSendAttendanceReport();
        return ResponseEntity.ok("Attendance report generated and sent successfully to Super Admin.");
    }

    @PutMapping("/staff/attendance/{attendanceId}/location")
    public ResponseEntity<com.caCommand.caCommand.entities.Attendance> updateAttendanceLocation(
            @PathVariable java.util.UUID attendanceId,
            @RequestBody java.util.Map<String, String> payload
    ) {
        String locationLink = payload.get("locationLink");
        String exitLocationLink = payload.get("exitLocationLink");
        return ResponseEntity.ok(adminTicketService.updateAttendanceLocation(attendanceId, locationLink, exitLocationLink));
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

    @PostMapping(value = "/clients/create-with-documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Client> createClientWithDocuments(
            @RequestParam("name") String name,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("city") String city,
            @RequestParam("pan") String pan,
            @RequestParam("dob") String dob,
            @RequestParam("itPassword") String itPassword,
            @RequestParam("serviceType") String serviceType,
            @RequestParam(value = "docNames", required = false) List<String> docNames,
            @RequestParam(value = "files", required = false) List<org.springframework.web.multipart.MultipartFile> files
    ) {
        // 1. Check if client with this phone number already exists
        Client client = clientRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (client == null) {
            client = new Client();
            client.setPhoneNumber(phoneNumber);
        }
        client.setName(name);
        client.setCity(city);
        client.setPan(pan.toUpperCase());
        client.setDob(dob);
        client.setItPassword(itPassword);
        client = clientRepository.save(client);

        // 2. Create a ticket for the client
        Ticket ticket = new Ticket();
        ticket.setClient(client);
        ticket.setServiceType(serviceType);
        
        boolean isCallService = !serviceType.toLowerCase().contains("itr");
        if (isCallService) {
            ticket.setStatus(com.caCommand.caCommand.enums.TicketStatus.CALL_PENDING.name());
            ticket.setTicketCategory("CALL_SERVICE");
        } else {
            ticket.setStatus(com.caCommand.caCommand.enums.TicketStatus.PENDING_ADMIN_APPROVAL.name());
            ticket.setTicketCategory("ITR");
        }
        
        String dateStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        String randomNum = String.format("%04d", new java.util.Random().nextInt(10000));
        ticket.setCaseId("CASE-" + dateStr + "-" + randomNum);
        ticket.setCaseStage(com.caCommand.caCommand.enums.CaseStage.ONBOARDING);

        // 3. Handle document uploads
        StringBuilder clientDocs = new StringBuilder();
        if (files != null && docNames != null && files.size() == docNames.size()) {
            for (int i = 0; i < files.size(); i++) {
                org.springframework.web.multipart.MultipartFile file = files.get(i);
                String docName = docNames.get(i);
                if (file != null && !file.isEmpty()) {
                    try {
                        String originalFilename = file.getOriginalFilename();
                        byte[] bytes = file.getBytes();
                        
                        // Upload to S3
                        String s3Key = client.getPhoneNumber() + "_" + System.currentTimeMillis() + "_" + originalFilename;
                        String s3Url = s3StorageService.uploadMedia(bytes, s3Key);
                        
                        if (s3Url != null) {
                            if (clientDocs.length() > 0) {
                                clientDocs.append("\n");
                            }
                            clientDocs.append(docName).append(" :: ").append(s3Url);
                        }
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        ticket.setClientDocuments(clientDocs.toString());
        ticketRepository.save(ticket);

        // Send WhatsApp notification to client
        try {
            String clientMessage = String.format(
                "🚩 *Jai Shree Ram* 🚩\n\n" +
                "Greetings %s,\n\n" +
                "Welcome to *Porwal CA Firm*.\n\n" +
                "We have successfully registered a new case for you:\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "🔹 *Case ID:* %s\n" +
                "🔹 *Service:* %s\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Our tax specialist team will review your details and contact you shortly. Thank you for choosing us! 🙏",
                client.getName(), ticket.getCaseId(), ticket.getServiceType()
            );
            whatsappMessageSender.sendMessage(client.getPhoneNumber(), clientMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Broadcast ticket update via WebSocket
        adminTicketService.broadcastUpdate();

        return ResponseEntity.ok(client);
    }

    public record CreateTaskRequest(String clientName, String clientPhoneNumber, String serviceType, String assignedStaffId, String notes) {}

    @PostMapping(value = "/tickets/create-task", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Ticket> createTask(
            @RequestParam("clientName") String clientName,
            @RequestParam("clientPhoneNumber") String clientPhoneNumber,
            @RequestParam("serviceType") String serviceType,
            @RequestParam("assignedStaffId") String assignedStaffId,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "files", required = false) org.springframework.web.multipart.MultipartFile[] files,
            @RequestParam(value = "fileNames", required = false) String[] fileNames
    ) {
        StringBuilder s3UrlsBuilder = new StringBuilder();
        String cleanPhone = clientPhoneNumber.replaceAll("[^0-9]", "");
        
        if (file != null && !file.isEmpty()) {
            try {
                String s3Key = cleanPhone + "_task_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                String s3Url = s3StorageService.uploadMedia(file.getBytes(), s3Key);
                if (s3Url != null) {
                    s3UrlsBuilder.append(s3Url);
                }
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
        
        if (files != null && files.length > 0) {
            for (int i = 0; i < files.length; i++) {
                org.springframework.web.multipart.MultipartFile f = files[i];
                if (f != null && !f.isEmpty()) {
                    try {
                        String s3Key = cleanPhone + "_task_" + System.currentTimeMillis() + "_" + f.getOriginalFilename();
                        String s3Url = s3StorageService.uploadMedia(f.getBytes(), s3Key);
                        if (s3Url != null) {
                            String customName = (fileNames != null && fileNames.length > i && fileNames[i] != null && !fileNames[i].isBlank()) 
                                    ? fileNames[i].trim() 
                                    : "Admin Document";
                            if (s3UrlsBuilder.length() > 0) {
                                s3UrlsBuilder.append("\n");
                            }
                            s3UrlsBuilder.append(customName).append(" :: ").append(s3Url);
                        }
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        Ticket t = adminTicketService.createTask(
            clientName,
            clientPhoneNumber,
            serviceType,
            assignedStaffId,
            notes,
            s3UrlsBuilder.length() > 0 ? s3UrlsBuilder.toString() : null
        );
        return ResponseEntity.ok(t);
    }

    @DeleteMapping("/tickets/{ticketId}")
    public ResponseEntity<String> deleteTicket(@PathVariable String ticketId) {
        adminTicketService.deleteTicket(ticketId);
        return ResponseEntity.ok("Ticket deleted successfully.");
    }
}
