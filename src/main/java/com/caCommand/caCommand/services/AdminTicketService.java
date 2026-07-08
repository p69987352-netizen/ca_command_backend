package com.caCommand.caCommand.services;

import com.caCommand.caCommand.dtos.AdminApprovalRequest;
import com.caCommand.caCommand.dtos.DeadlineRequest;
import com.caCommand.caCommand.dtos.NoteRequest;
import com.caCommand.caCommand.dtos.StaffClientMessageRequest;
import com.caCommand.caCommand.dtos.StaffProgressUpdateRequest;
import com.caCommand.caCommand.entities.ChatSession;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.Priority;
import com.caCommand.caCommand.enums.TicketStatus;
import com.caCommand.caCommand.exceptions.InvalidTicketStateException;
import com.caCommand.caCommand.exceptions.ResourceNotFoundException;
import com.caCommand.caCommand.repositories.ChatSessionRepository;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.StaffRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.repositories.PaymentHistoryRepository;
import com.caCommand.caCommand.repositories.ClientHistoryRepository;
import com.caCommand.caCommand.repositories.AttendanceRepository;
import com.caCommand.caCommand.services.WhatsAppMediaService;
import org.springframework.context.ApplicationEventPublisher;
import com.caCommand.caCommand.entities.PaymentHistory;
import com.caCommand.caCommand.entities.ClientHistory;
import com.caCommand.caCommand.services.S3StorageService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminTicketService {

    private static final Logger log = LoggerFactory.getLogger(AdminTicketService.class);
    private static final List<String> STAFF_ACTIVE_STATUSES = List.of(
            TicketStatus.ASSIGNED_TO_STAFF.name(),
            TicketStatus.PENDING_ADMIN_QC.name()
    );

    private final TicketRepository ticketRepository;
    private final StaffRepository staffRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final WhatsAppMessageSender whatsappMessageSender;
    private final ClientRepository clientRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ClientHistoryRepository clientHistoryRepository;
    private final AttendanceRepository attendanceRepository;
    private final WhatsAppMediaService whatsappMediaService;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final S3StorageService s3StorageService;

    @org.springframework.beans.factory.annotation.Value("${payment.qr.url:}")
    private String paymentQrUrl;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:5001}")
    private String baseUrl;

    public AdminTicketService(
            TicketRepository ticketRepository,
            StaffRepository staffRepository,
            ChatSessionRepository chatSessionRepository,
            WhatsAppMessageSender whatsappMessageSender,
            ClientRepository clientRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            ClientHistoryRepository clientHistoryRepository,
            AttendanceRepository attendanceRepository,
            WhatsAppMediaService whatsappMediaService,
            ApplicationEventPublisher eventPublisher,
            org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
            S3StorageService s3StorageService
    ) {
        this.ticketRepository = ticketRepository;
        this.staffRepository = staffRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.whatsappMessageSender = whatsappMessageSender;
        this.clientRepository = clientRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.clientHistoryRepository = clientHistoryRepository;
        this.attendanceRepository = attendanceRepository;
        this.whatsappMediaService = whatsappMediaService;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.s3StorageService = s3StorageService;
    }

    public void broadcastUpdate() {
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/updates", (Object) Map.of("type", "TICKET_UPDATE", "timestamp", System.currentTimeMillis()));
        }
    }

    private Ticket saveAndBroadcast(Ticket ticket) {
        Ticket saved = ticketRepository.save(ticket);
        broadcastUpdate();
        return saved;
    }

    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    @Transactional
    public Staff addNewStaff(Staff staff) {
        staffRepository.findByPhoneNumber(staff.getPhoneNumber()).ifPresent(existing -> {
            throw new InvalidTicketStateException("A staff member already exists with this phone number");
        });

        Staff savedStaff = staffRepository.save(staff);
        whatsappMessageSender.sendMessage(savedStaff.getPhoneNumber(),
                "Welcome to CA Command Center. You have been registered as a staff member.");
        log.info("Added staff member id={} phone={}", savedStaff.getId(), savedStaff.getPhoneNumber());
        return savedStaff;
    }

    public Optional<Staff> getStaffById(String staffIdentifier) {
        try {
            return Optional.of(resolveStaff(staffIdentifier));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void sendReminderToClient(String ticketId, String customMessage) {
        Ticket ticket = ticketRepository.findById(UUID.fromString(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        Client client = ticket.getClient();
        if (client != null && client.getPhoneNumber() != null) {
            String missingDocsMsg = "";
            
            if (customMessage != null && !customMessage.isBlank()) {
                missingDocsMsg = customMessage;
            } else {
                missingDocsMsg = "Kripya jaldi se pending documents ki clear photo ya PDF yahin WhatsApp par bhejein.";
                ChatSession session = chatSessionRepository.findById(client.getPhoneNumber()).orElse(null);
                if (session != null) {
                    List<String> required = GeminiService.SERVICE_DOCUMENTS.get(ticket.getServiceType());
                    if (required != null) {
                        List<String> received = new ArrayList<>();
                        if (session.getVerifiedDocumentTypes() != null && !session.getVerifiedDocumentTypes().isBlank()) {
                            received.addAll(Arrays.asList(session.getVerifiedDocumentTypes().split(",")));
                        }
                        List<String> missing = new ArrayList<>();
                        for (String req : required) {
                            if (!received.contains(req)) {
                                missing.add(req);
                            }
                        }
                        if (!missing.isEmpty()) {
                            StringBuilder bulletList = new StringBuilder("Missing Documents:\n");
                            for (String m : missing) {
                                bulletList.append("• ").append(m).append("\n");
                            }
                            missingDocsMsg = "Pending Documents ki list neeche hai. Kripya jaldi se unki clear photo ya PDF yahin WhatsApp par bhejein.\n\n" + bulletList.toString();
                        }
                    }
                }
            }

            whatsappMessageSender.sendMessage(client.getPhoneNumber(),
                    "🔔 *Reminder from Phorwal CA Firm*\n\n" +
                    "Aapki " + ticket.getServiceType() + " file ke liye kuch required documents abhi tak pending hain ya verification baki hai.\n\n" +
                    missingDocsMsg + "\n\nTaaki hum aapka kaam aage badha sakein. 🙏");
        }
    }
    @Transactional
    public void removeStaff(String staffIdentifier) {
        Staff staff = resolveStaff(staffIdentifier);
        staff.setIsActive(false);
        staffRepository.save(staff);
        log.info("Soft deleted staff member id={}", staff.getId());
    }

    public com.caCommand.caCommand.dtos.StaffPerformanceDTO getStaffPerformance(String staffId) {
        Staff staff = resolveStaff(staffId);
        com.caCommand.caCommand.dtos.StaffPerformanceDTO dto = new com.caCommand.caCommand.dtos.StaffPerformanceDTO();
        dto.setStaffId(staff.getId().toString());
        dto.setStaffName(staff.getName());
        
        List<Ticket> staffTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getAssignedStaff() != null && t.getAssignedStaff().getId().equals(staff.getId()))
                .toList();

        long completed = staffTickets.stream().filter(t -> com.caCommand.caCommand.enums.TicketStatus.COMPLETED.name().equals(t.getStatus())).count();
        long pending = staffTickets.stream().filter(t -> !com.caCommand.caCommand.enums.TicketStatus.COMPLETED.name().equals(t.getStatus())).count();
        // Since reassigned is tricky to track without a specific history table, we'll keep it simple for now or derive from logs. Let's say 0.
        long reassigned = 0; 
        
        dto.setTotalCompleted(completed);
        dto.setTotalPending(pending);
        dto.setTotalReassigned(reassigned);

        java.time.LocalDate startOfMonth = java.time.LocalDate.now().withDayOfMonth(1);
        java.time.LocalDate endOfMonth = java.time.LocalDate.now().withDayOfMonth(java.time.LocalDate.now().lengthOfMonth());
        
        List<com.caCommand.caCommand.entities.Attendance> monthAttendance = attendanceRepository.findByStaffAndAttendanceDateBetweenOrderByAttendanceDateDesc(staff, startOfMonth, endOfMonth);
        
        long daysPresent = monthAttendance.stream().filter(a -> com.caCommand.caCommand.enums.AttendanceStatus.PRESENT.equals(a.getStatus())).count();
        long daysAbsent = monthAttendance.stream().filter(a -> com.caCommand.caCommand.enums.AttendanceStatus.ABSENT.equals(a.getStatus())).count();
        
        dto.setDaysPresent(daysPresent);
        dto.setDaysAbsent(daysAbsent);
        
        return dto;
    }

    public List<com.caCommand.caCommand.entities.Attendance> getStaffAttendance(String staffId) {
        Staff staff = resolveStaff(staffId);
        return signAttendanceUrls(attendanceRepository.findByStaffOrderByAttendanceDateDesc(staff));
    }

    public List<Ticket> getStaffTickets(String staffId) {
        Staff staff = resolveStaff(staffId);
        return ticketRepository.findByAssignedStaffIdOrderByCreatedAtDesc(staff.getId());
    }

    public List<com.caCommand.caCommand.entities.Attendance> getTodayAttendance() {
        return signAttendanceUrls(attendanceRepository.findByAttendanceDate(java.time.LocalDate.now()));
    }

    public List<com.caCommand.caCommand.entities.Attendance> getAttendanceByDate(java.time.LocalDate date) {
        return signAttendanceUrls(attendanceRepository.findByAttendanceDate(date));
    }

    public List<com.caCommand.caCommand.entities.Attendance> getAttendanceByMonth(int year, int month) {
        java.time.LocalDate startOfMonth = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
        return signAttendanceUrls(attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateDesc(startOfMonth, endOfMonth));
    }

    private List<com.caCommand.caCommand.entities.Attendance> signAttendanceUrls(List<com.caCommand.caCommand.entities.Attendance> list) {
        if (list == null) return null;
        for (com.caCommand.caCommand.entities.Attendance attendance : list) {
            if (attendance.getPhotoUrl() != null && !attendance.getPhotoUrl().isBlank()) {
                attendance.setPhotoUrl(s3StorageService.getSignedUrl(attendance.getPhotoUrl()));
            }
        }
        return list;
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public void sendManualAttendanceReminders() {
        List<Staff> activeStaff = staffRepository.findAll().stream().filter(Staff::getIsActive).toList();
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        for (Staff staff : activeStaff) {
            java.util.Optional<com.caCommand.caCommand.entities.Attendance> attOpt = attendanceRepository.findByStaffAndAttendanceDate(staff, today);
            boolean marked = attOpt.isPresent() && attOpt.get().getStatus() != com.caCommand.caCommand.enums.AttendanceStatus.NOT_MARKED;
            if (!marked) {
                String message = String.format("🚩 *Jay Shree Ram* 🚩\n\n" +
                        "Greetings,\n\n" +
                        "📖 *%s*\n\n" +
                        "Please mark your attendance by sending a photo. 📸\n\n" +
                        "If you are not coming to the office today, reply with *NO <reason>* (e.g. *NO sick*). 🛑", 
                        getRandomGitaQuote());
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), message);
                log.info("Sent manual attendance reminder to {}", staff.getPhoneNumber());
            }
        }
    }

    private String getRandomGitaQuote() {
        String[] quotes = {
            "कर्मण्येवाधिकारस्ते मा फलेषु कदाचन।\n(You have the right to perform your prescribed duty, but you are not entitled to the fruits of action.) - Bhagavad Gita",
            "क्रोधाद्भवति सम्मोहः सम्मोहात्स्मृतिविभ्रमः।\n(Anger leads to clouding of judgment, which results in bewilderment of the memory.) - Bhagavad Gita",
            "यदा यदा हि धर्मस्य ग्लानिर्भवति भारत।\n(Whenever there is a decline in righteousness, O Arjuna, I manifest myself on earth.) - Bhagavad Gita",
            "उद्धरेदात्मनात्मानं नात्मानमवसादयेत्।\n(Elevate yourself through the power of your mind, and not degrade yourself, for the mind can be the friend and also the enemy of the self.) - Bhagavad Gita"
        };
        return quotes[new java.util.Random().nextInt(quotes.length)];
    }

    public List<Ticket> getPendingTickets() {
        return ticketRepository.findByStatus(TicketStatus.PENDING_ADMIN_APPROVAL.name());
    }

    public List<Ticket> getInProgressTickets() {
        return ticketRepository.findByStatus(TicketStatus.IN_PROGRESS.name());
    }

    public String getPresignedDocumentUrl(String ticketId) {
        Ticket ticket = ticketRepository.findById(java.util.UUID.fromString(ticketId)).orElseThrow(() -> new RuntimeException("Ticket not found"));
        String raw = ticket.getStaffSubmittedDocument();
        if (raw != null && raw.startsWith("s3://")) {
            return s3StorageService.getSignedUrl(raw);
        }
        return raw;
    }

    public List<Ticket> getAwaitingPaymentTickets() {
        return ticketRepository.findByStatus(TicketStatus.AWAITING_PAYMENT.name());
    }

    public List<Ticket> getPaidTickets() {
        List<Ticket> paymentReceived = ticketRepository.findByStatus(TicketStatus.PAYMENT_RECEIVED.name());
        List<Ticket> inProgress = ticketRepository.findByStatus(TicketStatus.IN_PROGRESS.name());
        List<Ticket> combined = new java.util.ArrayList<>(paymentReceived);
        combined.addAll(inProgress);
        return combined;
    }

    public List<Ticket> getQCPendingTickets() {
        return ticketRepository.findByStatus(TicketStatus.PENDING_ADMIN_QC.name());
    }

    public List<Ticket> getCompletedTickets() {
        return ticketRepository.findByStatus(TicketStatus.FINISHED.name());
    }

    public List<Ticket> getInProgressTicketsForStaff(String staffIdentifier) {
        Staff staff = resolveStaff(staffIdentifier);
        return ticketRepository.findByAssignedStaffIdAndStatusIn(staff.getId(), STAFF_ACTIVE_STATUSES);
    }

    public List<Ticket> getWorkQueueForStaff(String staffIdentifier) {
        Staff staff = resolveStaff(staffIdentifier);
        return ticketRepository.findByAssignedStaffIdAndStatusIn(staff.getId(), STAFF_ACTIVE_STATUSES);
    }

    public Optional<Ticket> getTicketById(String ticketId) {
        try {
            return Optional.of(resolveTicket(ticketId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Ticket resolveTicket(String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return getRequiredTicket(id);
        } catch (IllegalArgumentException e) {
            String phone = identifier.replaceAll("[^0-9]", "");
            if (phone.length() < 10) {
                throw new ResourceNotFoundException("Invalid identifier or phone number: " + identifier);
            }
            List<Ticket> tickets = ticketRepository.findByClientPhoneNumber(phone);
            if (tickets.isEmpty()) {
                throw new ResourceNotFoundException("No ticket found for client phone: " + phone);
            }
            return tickets.stream()
                    .filter(t -> !TicketStatus.FINISHED.name().equals(t.getStatus()))
                    .findFirst()
                    .orElse(tickets.get(0));
        }
    }

    @Transactional
    public Ticket approveTicketAndSetFee(String ticketId, AdminApprovalRequest request) {
        Ticket ticket = resolveTicket(ticketId);
        if (!TicketStatus.PENDING_ADMIN_APPROVAL.name().equals(ticket.getStatus()) 
                && !TicketStatus.CALL_PENDING.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException(
                    "Ticket must be PENDING_ADMIN_APPROVAL or CALL_PENDING. Current status: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.AWAITING_PAYMENT.name());
        ticket.setQuotedFee(request.getFeeAmount());
        ticket.setAdminNotes(request.getAdminNotes());

        if (request.getOverrideReason() != null && !request.getOverrideReason().isBlank()) {
            ticket.setAdminFinalFee(request.getFeeAmount());
            ticket.setAdminOverrideReason(request.getOverrideReason());
            ticket.setOverriddenBy(request.getOverriddenBy() != null ? request.getOverriddenBy() : "Admin");
            ticket.setOverriddenAt(LocalDateTime.now());
        }
        
        ticket.setPaymentStatus("PAYMENT_PENDING");
        Ticket updatedTicket = saveAndBroadcast(ticket);

        String messageBody = formatMessage(
                "🚩 *Jay Shree Ram* 🚩",
                "",
                "💳 *PAYMENT REQUESTED*",
                "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "🔹 *Service:* " + ticket.getServiceType(),
                "🔹 *Fee:* Rs. " + String.format("%.2f", request.getFeeAmount()),
                "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "",
                "Kripya niche diye gaye QR code ka use karke payment karein.",
                "",
                "Payment complete hone ke baad, please apna Screenshot ya UTR number yahan reply mein share/upload karein taaki hum verify karke work process aage badha sakein. 🙏"
        );

        if (paymentQrUrl != null && !paymentQrUrl.isBlank()) {
            whatsappMessageSender.sendImageMessage(ticket.getClient().getPhoneNumber(), paymentQrUrl, messageBody);
        } else {
            // Fallback just in case URL isn't set
            whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), messageBody);
        }

        log.info("Requested payment for ticket id={} quotedFee={}", ticketId, request.getFeeAmount());
        return updatedTicket;
    }

    public Ticket approveTicket(String ticketId, Integer feeAmount) {
        AdminApprovalRequest request = new AdminApprovalRequest();
        request.setFeeAmount(feeAmount.doubleValue());
        return approveTicketAndSetFee(ticketId, request);
    }

    @Transactional
    public Ticket markPaymentSuccessful(String ticketId) {
        Ticket ticket = resolveTicket(ticketId);

        if (!TicketStatus.PAYMENT_RECEIVED.name().equals(ticket.getStatus())
                && !TicketStatus.AWAITING_PAYMENT.name().equals(ticket.getStatus())
                && !TicketStatus.PAYMENT_VERIFICATION_PENDING.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException(
                    "Ticket is not awaiting payment. Current status: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.IN_PROGRESS.name());
        ticket.setPaymentStatus("PAID");
        ticket.setPaymentCompletedAt(LocalDateTime.now());
        Ticket updatedTicket = saveAndBroadcast(ticket);

        PaymentHistory ph = new PaymentHistory();
        ph.setClient(ticket.getClient());
        ph.setTicket(ticket);
        ph.setAmount(ticket.getQuotedFee());
        ph.setPaymentLink(ticket.getPaymentLink());
        ph.setStatus("SUCCESS");
        ph.setTransactionReference(ticket.getPaymentReferenceId());
        ph.setPaidAt(LocalDateTime.now());
        paymentHistoryRepository.save(ph);

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "Payment verified successfully.",
                "Service: " + ticket.getServiceType(),
                "Our team has started processing your file."
        ));

        log.info("Payment marked successful for ticket id={}", ticketId);
        return updatedTicket;
    }

    @Transactional
    public Ticket rejectPaymentProof(String ticketId) {
        Ticket ticket = resolveTicket(ticketId);

        if (!TicketStatus.PAYMENT_VERIFICATION_PENDING.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException(
                    "Ticket is not pending payment verification. Current status: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.AWAITING_PAYMENT.name());
        ticket.setPaymentProofUrl(null);
        ticket.setPaymentReferenceId(null);
        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "dY\"t *PAYMENT VERIFICATION FAILED*",
                "Sorry, hum aapki payment verify nahi kar paaye.",
                "Kripya dobara payment screenshot ya UTR number check karke bhejein."
        ));

        log.info("Payment rejected for ticket id={}", ticketId);
        return updatedTicket;
    }

    public Ticket verifyPayment(String ticketId) {
        return markPaymentSuccessful(ticketId);
    }

    @Transactional
    public Ticket assignToStaff(String ticketId, String staffIdentifier, com.caCommand.caCommand.dtos.AssignTicketRequest payload) {
        Ticket ticket = resolveTicket(ticketId);
        Staff staff = resolveStaff(staffIdentifier);

        if (!TicketStatus.IN_PROGRESS.name().equals(ticket.getStatus()) && !TicketStatus.CALL_PENDING.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException("Ticket must be IN_PROGRESS or CALL_PENDING. Current status: " + ticket.getStatus());
        }

        String priority = payload != null && payload.priority() != null ? payload.priority() : "Normal";
        String notes = payload != null && payload.notes() != null ? payload.notes() : "";
        String normalizedPriority = Priority.normalize(priority);
        
        ticket.setAssignedStaff(staff);
        if (!TicketStatus.CALL_PENDING.name().equals(ticket.getStatus())) {
            ticket.setStatus(TicketStatus.ASSIGNED_TO_STAFF.name());
        }
        ticket.setPriority(normalizedPriority);
        ticket.setAdminNotes(notes);
        ticket.setProgressPercent(10);

        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "Your file has been assigned.",
                "Service: " + ticket.getServiceType(),
                "Assigned staff: " + staff.getName()
        ));

        String aisPdfLink = ticket.getAisPdfPath() != null ? "\n📄 *AIS/TIS Report:* " + baseUrl + "/api/admin/tickets/" + ticketId + "/download-pdf" : "";
        
        String deadline = payload != null && payload.deadline() != null ? payload.deadline() : "Not specified";
        String language = payload != null && payload.language() != null ? payload.language() : "English/Hindi";

        whatsappMessageSender.sendMessage(staff.getPhoneNumber(), formatMessage(
                "👨‍💼 *New Assignment: " + ticket.getCaseId() + "*",
                "Client: " + nullToDefault(ticket.getClient().getName(), ticket.getClient().getPhoneNumber()),
                "Service: " + ticket.getServiceType(),
                "",
                "🔑 *Client Portal Info:*",
                "PAN: " + nullToDefault(ticket.getClient().getPan(), "N/A"),
                "DOB: " + nullToDefault(ticket.getClient().getDob(), "N/A"),
                "Password: " + nullToDefault(ticket.getClient().getItPassword(), "N/A"),
                "",
                "Priority: " + (normalizedPriority.equalsIgnoreCase("HIGH") ? "🔴 " : "") + normalizedPriority,
                "Deadline: " + deadline,
                "Language: " + language,
                "Notes: " + nullToDefault(notes, "No additional notes."),
                aisPdfLink
        ));

        log.info("Assigned ticket id={} to staff id={} priority={}", ticketId, staffIdentifier, normalizedPriority);
        return updatedTicket;
    }

    @Transactional
    public Ticket reassignToStaff(String ticketId, String staffIdentifier, String notes) {
        Ticket ticket = resolveTicket(ticketId);
        Staff staff = resolveStaff(staffIdentifier);

        Staff previousStaff = ticket.getAssignedStaff();
        ticket.setAssignedStaff(staff);
        ticket.setStatus(TicketStatus.ASSIGNED_TO_STAFF.name());
        ticket.setAdminNotes(appendLog(ticket.getAdminNotes(), notes));
        ticket.setRevisionNotes(appendLog(ticket.getRevisionNotes(), "Reassigned to " + staff.getName() + ". " + nullToDefault(notes, "")));
        ticket.setProgressPercent(60);

        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(staff.getPhoneNumber(), formatMessage(
                "File reassigned to you for review/correction.",
                "Client: " + ticket.getClient().getPhoneNumber(),
                "Service: " + ticket.getServiceType(),
                "Client documents:",
                formatDocumentList(ticket.getClientDocuments()),
                "",
                "Previous staff work:",
                ticket.getStaffSubmittedDocument() != null ? s3StorageService.getSignedUrl(ticket.getStaffSubmittedDocument()) : "No previous final work link available.",
                "",
                "Admin note:",
                nullToDefault(notes, "Please cross-check and resubmit final work.")
        ));

        if (previousStaff != null && !previousStaff.getId().equals(staff.getId())) {
            whatsappMessageSender.sendMessage(previousStaff.getPhoneNumber(), "Ticket " + ticket.getId() + " has been reassigned for additional review.");
        }

        return updatedTicket;
    }

    @Transactional
    public Ticket sendAdminNote(String ticketId, NoteRequest request) {
        Ticket ticket = resolveTicket(ticketId);
        String recipient = request.recipient().trim().toUpperCase();
        String note = request.note().trim();

        switch (recipient) {
            case "CLIENT" -> {
                whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), "Note from Phorwal CA Firm:\n" + note);
                ticket.setClientRequestLog(appendLog(ticket.getClientRequestLog(), "ADMIN_NOTE_TO_CLIENT: " + note));
            }
            case "STAFF" -> {
                if (ticket.getAssignedStaff() == null) {
                    throw new InvalidTicketStateException("Ticket has no assigned staff member");
                }
                whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(), "Admin note:\n" + note);
                ticket.setAdminStaffMessage(appendLog(ticket.getAdminStaffMessage(), note));
            }
            default -> throw new IllegalArgumentException("Recipient must be CLIENT or STAFF");
        }

        return saveAndBroadcast(ticket);
    }

    @Transactional
    public Ticket submitWorkForQC(String ticketId, String documentUrl) {
        Ticket ticket = resolveTicket(ticketId);
        requireStatus(ticket, TicketStatus.ASSIGNED_TO_STAFF);

        if (documentUrl == null || documentUrl.isBlank()) {
            throw new IllegalArgumentException("Document URL is required");
        }

        ticket.setStaffSubmittedDocument(documentUrl.trim());
        ticket.setStatus(TicketStatus.PENDING_ADMIN_QC.name());
        ticket.setProgressPercent(90);
        ticket.setStaffUpdate(appendLog(ticket.getStaffUpdate(), "Final work submitted for admin QC."));
        Ticket updatedTicket = saveAndBroadcast(ticket);

        log.info("Submitted ticket id={} for admin QC", ticketId);
        return updatedTicket;
    }

    @Transactional
    public Ticket approveAndDeliver(String ticketId, String closingMessage) {
        Ticket ticket = resolveTicket(ticketId);
        if (!TicketStatus.PENDING_ADMIN_QC.name().equals(ticket.getStatus())
                && !TicketStatus.ASSIGNED_TO_STAFF.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException("Ticket is not ready for delivery. Current status: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.FINISHED.name());
        ticket.setCompletedAt(LocalDateTime.now());
        ticket.setDeliveredAt(LocalDateTime.now());
        ticket.setProgressPercent(100);

        Ticket finishedTicket = saveAndBroadcast(ticket);
        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "Work completed and delivered.",
                "Service: " + ticket.getServiceType(),
                "Message: " + nullToDefault(closingMessage, "Your work has been completed as per requirements."),
                "Final document: " + (ticket.getStaffSubmittedDocument() != null ? s3StorageService.getSignedUrl(ticket.getStaffSubmittedDocument()) : "Contact support for final document link.")
        ));

        if (ticket.getAssignedStaff() != null) {
            whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(),
                    "Ticket closed successfully for client " + ticket.getClient().getPhoneNumber());
        }

        chatSessionRepository.deleteById(ticket.getClient().getPhoneNumber());
        log.info("Delivered and closed ticket id={}", ticketId);
        return finishedTicket;
    }

    @Transactional
    public Ticket setDeadline(String ticketId, DeadlineRequest request) {
        Ticket ticket = resolveTicket(ticketId);
        ticket.setDeadlineAt(request.deadlineAt());
        if (request.priority() != null && !request.priority().isBlank()) {
            ticket.setPriority(Priority.normalize(request.priority()));
        }
        if (request.note() != null && !request.note().isBlank()) {
            ticket.setAdminNotes(appendLog(ticket.getAdminNotes(), request.note()));
        }

        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "Your work timeline has been updated.",
                "Service: " + ticket.getServiceType(),
                "Expected delivery: " + ticket.getDeadlineAt(),
                "Priority: " + ticket.getPriority(),
                "",
                "Urgent requirement ho to yahin reply karein. Team priority/fees accordingly confirm karegi."
        ));

        if (ticket.getAssignedStaff() != null) {
            whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(), formatMessage(
                    "Deadline updated for assigned file.",
                    "Client: " + ticket.getClient().getPhoneNumber(),
                    "Service: " + ticket.getServiceType(),
                    "Deadline: " + ticket.getDeadlineAt(),
                    "Priority: " + ticket.getPriority()
            ));
        }

        return updatedTicket;
    }

    @Transactional
    public Ticket askStaffForUpdate(String ticketId, String adminMessage) {
        Ticket ticket = resolveTicket(ticketId);
        if (ticket.getAssignedStaff() == null) {
            throw new InvalidTicketStateException("Ticket has no assigned staff member");
        }

        String message = nullToDefault(adminMessage, "Please share current progress update.");
        ticket.setAdminStaffMessage(appendLog(ticket.getAdminStaffMessage(), message));
        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(), formatMessage(
                "Admin is asking for an update.",
                "Client: " + ticket.getClient().getPhoneNumber(),
                "Service: " + ticket.getServiceType(),
                "Message: " + message,
                "",
                "Reply in dashboard/WhatsApp with STATUS update or submit revised document if ready."
        ));

        return updatedTicket;
    }

    @Transactional
    public Ticket staffMessageClient(String ticketId, StaffClientMessageRequest request) {
        Ticket ticket = resolveTicket(ticketId);
        ensureAssigned(ticket);

        String type = request.type().trim().toUpperCase();
        String prefix = switch (type) {
            case "NEED" -> "CA team needs this document/info:";
            case "QUERY" -> "CA team has a query:";
            default -> "CA team update:";
        };

        String clientMessage = formatMessage(
                prefix,
                request.message(),
                "",
                "Please reply here on WhatsApp so your file can move forward."
        );

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), clientMessage);
        ticket.setClientRequestLog(appendLog(ticket.getClientRequestLog(), type + ": " + request.message()));
        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(), "Message sent to client.");
        return updatedTicket;
    }

    @Transactional
    public Ticket staffUpdateProgress(String ticketId, StaffProgressUpdateRequest request) {
        Ticket ticket = resolveTicket(ticketId);
        ensureAssigned(ticket);

        ticket.setProgressPercent(request.progressPercent());
        ticket.setStaffUpdate(appendLog(ticket.getStaffUpdate(), request.progressPercent() + "% - " + request.updateMessage()));
        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "Work update from Phorwal CA Firm:",
                request.updateMessage(),
                "Progress: " + request.progressPercent() + "%"
        ));

        return updatedTicket;
    }

    @Transactional
    public Ticket requestStaffChanges(String ticketId, String changeRequest) {
        Ticket ticket = resolveTicket(ticketId);
        if (!TicketStatus.PENDING_ADMIN_QC.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException("Only QC pending tickets can be sent back for changes. Current status: " + ticket.getStatus());
        }

        if (ticket.getAssignedStaff() == null) {
            throw new InvalidTicketStateException("Ticket has no assigned staff member");
        }

        String feedback = nullToDefault(changeRequest, "Admin requested changes. Please review and resubmit final work.");
        ticket.setStatus(TicketStatus.ASSIGNED_TO_STAFF.name());
        ticket.setAdminNotes(feedback);
        ticket.setRevisionNotes(appendLog(ticket.getRevisionNotes(), feedback));
        ticket.setProgressPercent(75);
        Ticket updatedTicket = saveAndBroadcast(ticket);

        whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(), formatMessage(
                "Admin requested changes for assigned client file.",
                "Service: " + ticket.getServiceType(),
                "Client phone: " + ticket.getClient().getPhoneNumber(),
                "Feedback: " + feedback,
                "",
                "Please make corrections and upload the revised final document."
        ));

        log.info("Requested staff changes for ticket id={}", ticketId);
        return updatedTicket;
    }

    @Transactional
    public Ticket completeTicketAndDeliver(String ticketId, String finalDocumentUrl, String closingMessage) {
        Ticket ticket = resolveTicket(ticketId);

        if (finalDocumentUrl != null && !finalDocumentUrl.isBlank()) {
            ticket.setStaffSubmittedDocument(finalDocumentUrl.trim());
        }

        // Validate state
        if (!TicketStatus.PENDING_ADMIN_QC.name().equals(ticket.getStatus())
                && !TicketStatus.ASSIGNED_TO_STAFF.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException("Ticket is not ready for delivery. Current status: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.FINISHED.name());
        ticket.setCompletedAt(LocalDateTime.now());
        ticket.setDeliveredAt(LocalDateTime.now());
        ticket.setProgressPercent(100);

        Ticket finishedTicket = saveAndBroadcast(ticket);
        String finalUrl = ticket.getStaffSubmittedDocument() != null ? s3StorageService.getSignedUrl(ticket.getStaffSubmittedDocument()) : "Contact support for final document link.";
        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), formatMessage(
                "\uD83C\uDF89 Work completed and delivered!",
                "Service: " + ticket.getServiceType(),
                "Message: " + nullToDefault(closingMessage, "Your work has been completed as per requirements."),
                "",
                "📄 *Download your final document here:*",
                "Tap the link below to view or download:",
                finalUrl
        ));

        if (ticket.getAssignedStaff() != null) {
            whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(),
                    "\u2705 Ticket closed successfully for client " + ticket.getClient().getPhoneNumber());
        }

        chatSessionRepository.deleteById(ticket.getClient().getPhoneNumber());
        log.info("Delivered and closed ticket id={}", ticketId);
        return finishedTicket;
    }

    public Map<String, Long> getTicketStatistics() {
        return Map.of(
                "total", ticketRepository.count(),
                "pending", ticketRepository.countByStatus(TicketStatus.PENDING_ADMIN_APPROVAL.name()),
                "awaiting_payment", ticketRepository.countByStatus(TicketStatus.AWAITING_PAYMENT.name()),
                "payment_received", ticketRepository.countByStatus(TicketStatus.PAYMENT_RECEIVED.name()),
                "in_progress", ticketRepository.countByStatus(TicketStatus.IN_PROGRESS.name())
                        + ticketRepository.countByStatus(TicketStatus.ASSIGNED_TO_STAFF.name()),
                "pending_qc", ticketRepository.countByStatus(TicketStatus.PENDING_ADMIN_QC.name()),
                "completed", ticketRepository.countByStatus(TicketStatus.FINISHED.name())
        );
    }
    
    private void saveClientHistory(Ticket ticket, String closingMessage) {
        ClientHistory history = new ClientHistory();
        history.setClient(ticket.getClient());
        history.setServiceType(ticket.getServiceType());
        history.setFeeCharged(ticket.getQuotedFee());
        if (ticket.getAssignedStaff() != null) {
            history.setAssignedCa(ticket.getAssignedStaff());
        }
        history.setCompletionDate(LocalDateTime.now());
        history.setFinalSummary(closingMessage);
        history.setRiskScore(String.valueOf(ticket.getReadinessScore())); // Or risk score if available
        clientHistoryRepository.save(history);
        
        // Update client analytics
        Client client = ticket.getClient();
        client.setTotalCases(client.getTotalCases() + 1);
        client.setCompletedCases(client.getCompletedCases() + 1);
        if (ticket.getQuotedFee() != null) {
            client.setTotalRevenueGenerated(client.getTotalRevenueGenerated() + ticket.getQuotedFee());
        }
        client.setAverageCaseValue(client.getTotalRevenueGenerated() / client.getTotalCases());
        client.setLastServiceDate(LocalDateTime.now());
        clientRepository.save(client);
    }

    public Staff resolveStaff(String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return staffRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id));
        } catch (IllegalArgumentException e) {
            String phone = identifier.replaceAll("[^0-9]", "");
            if (phone.length() < 10) {
                throw new ResourceNotFoundException("Invalid staff identifier or phone number: " + identifier);
            }
            return staffRepository.findByPhoneNumber(phone)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found for phone: " + phone));
        }
    }

    private Ticket getRequiredTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
    }

    private void requireStatus(Ticket ticket, TicketStatus requiredStatus) {
        if (!requiredStatus.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException(
                    "Ticket must be " + requiredStatus.name() + ". Current status: " + ticket.getStatus());
        }
    }

    private void ensureAssigned(Ticket ticket) {
        if (ticket.getAssignedStaff() == null) {
            throw new InvalidTicketStateException("Ticket has no assigned staff member");
        }
        if (!TicketStatus.ASSIGNED_TO_STAFF.name().equals(ticket.getStatus())
                && !TicketStatus.PENDING_ADMIN_QC.name().equals(ticket.getStatus())
                && !TicketStatus.CALL_PENDING.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException("Ticket is not in staff workflow. Current status: " + ticket.getStatus());
        }
    }

    private String appendLog(String current, String entry) {
        if (entry == null || entry.isBlank()) {
            return current;
        }
        String stamped = LocalDateTime.now() + " - " + entry.trim();
        return current == null || current.isBlank() ? stamped : current + "\n" + stamped;
    }

    private String formatMessage(String... lines) {
        return String.join("\n", lines);
    }

    private String formatDocumentList(String documentsString) {
        if (documentsString == null || documentsString.isBlank()) {
            return "No documents uploaded yet";
        }

        String[] docs = documentsString.split("\\r?\\n|,");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < docs.length; i++) {
            String doc = docs[i].trim();
            if (doc.isBlank()) {
                continue;
            }
            builder.append(i + 1).append(". ").append(doc);
            if (i < docs.length - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    // ======================================================
    // 🔴 CREDENTIAL REQUEST
    // ======================================================

    /**
     * Admin requests credentials from client (IT portal login, Form 16 creds).
     * Sets credentialStatus = REQUESTED. WhatsApp message sent to client.
     * Red dot appears on dashboard card.
     */
    @Transactional
    public Ticket requestCredentials(String ticketIdentifier, String credentialLabel) {
        Ticket ticket = resolveTicket(ticketIdentifier);
        String label = (credentialLabel != null && !credentialLabel.isBlank())
                ? credentialLabel.trim()
                : "IT Portal Login ID & Password";

        ticket.setCredentialStatus("REQUESTED");
        ticket.setCredentialRequestedAt(LocalDateTime.now());
        ticket.setCredentialRequestLabel(label);
        ticket = saveAndBroadcast(ticket);

        String clientPhone = ticket.getClient().getPhoneNumber();
        String clientName = ticket.getClient().getName() != null ? ticket.getClient().getName() : "Client";
        String service = ticket.getServiceType();

        String msg = "🔐 *Credential Request — " + service + "*\n\n" +
                "Namaste " + clientName + " ji 🙏\n\n" +
                "Aapki file process karne ke liye humein aapke *" + label + "* chahiye.\n\n" +
                "Please apna Login ID aur Password yahan type kar dein:\n" +
                "Example:\n" +
                "*ID:* yourloginid@email.com\n" +
                "*Password:* yourpassword\n\n" +
                "⚠️ Yeh information sirf aapki ITR filing ke liye use hogi aur poori tarah secure hai.";
        whatsappMessageSender.sendMessage(clientPhone, msg);
        log.info("Credential requested for ticket={} label={}", ticket.getId(), label);
        return ticket;
    }

    // ======================================================
    // 📜 CLIENT HISTORY
    // ======================================================

    /**
     * Get complete client history by phone or name query.
     */
    public Map<String, Object> getClientHistory(String query) {
        List<Client> clients = clientRepository.searchByPhoneOrName(query);
        Map<String, Object> result = new LinkedHashMap<>();

        if (clients.isEmpty()) {
            result.put("found", false);
            result.put("query", query);
            result.put("message", "No client found matching: " + query);
            return result;
        }

        // Take first match
        Client client = clients.get(0);
        List<Ticket> allTickets = ticketRepository.findAllByClientIdOrderByCreatedAtDesc(client.getId());

        long totalCompleted = allTickets.stream()
                .filter(t -> "FINISHED".equals(t.getStatus()) || "COMPLETED".equals(t.getStatus()))
                .count();
        long totalActive = allTickets.stream()
                .filter(t -> TicketStatus.isActive(t.getStatus()))
                .count();
        double totalAmountPaid = allTickets.stream()
                .filter(t -> "FINISHED".equals(t.getStatus()) || "COMPLETED".equals(t.getStatus()))
                .mapToDouble(t -> t.getQuotedFee() != null ? t.getQuotedFee() : 0)
                .sum();

        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("id", client.getId());
        clientInfo.put("name", client.getName());
        clientInfo.put("phoneNumber", client.getPhoneNumber());
        clientInfo.put("city", client.getCity());
        clientInfo.put("pinCode", client.getPinCode());
        clientInfo.put("clientType", client.getClientType());
        clientInfo.put("incomeRange", client.getIncomeRange());
        clientInfo.put("memberSince", client.getCreatedAt());
        clientInfo.put("lastServiceDate", client.getLastServiceDate());

        result.put("found", true);
        result.put("client", clientInfo);
        result.put("stats", Map.of(
                "totalTickets", allTickets.size(),
                "totalCompleted", totalCompleted,
                "totalActive", totalActive,
                "totalAmountPaid", Math.round(totalAmountPaid)
        ));
        result.put("tickets", allTickets);
        result.put("allMatches", clients.size());
        return result;
    }

    // ==========================================================
    // CALL-BASED FLOW METHODS (Non-ITR)
    // ==========================================================

    /**
     * Mark a CALL_PENDING ticket as CALL_DONE and save notes
     */
    @Transactional
    public Ticket markCallDone(String ticketId, String notes) {
        Ticket ticket = ticketRepository.findById(UUID.fromString(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (!TicketStatus.CALL_PENDING.name().equals(ticket.getStatus())) {
            throw new InvalidTicketStateException("Ticket is not in CALL_PENDING state. Current: " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.CALL_DONE.name());
        ticket.setCallNotes(notes);
        ticket.setCallCompletedAt(LocalDateTime.now());
        ticket.setCompletedAt(LocalDateTime.now());
        ticket.setProgressPercent(100);
        
        return saveAndBroadcast(ticket);
    }

    /**
     * Get all CALL_PENDING tickets
     */
    public List<Ticket> getCallPendingTickets() {
        return ticketRepository.findByStatusOrderByCreatedAtDesc(TicketStatus.CALL_PENDING.name());
    }
}
