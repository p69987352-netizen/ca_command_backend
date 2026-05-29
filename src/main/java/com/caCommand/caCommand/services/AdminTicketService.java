package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.repositories.ChatSessionRepository;
import com.caCommand.caCommand.repositories.StaffRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final StaffRepository staffRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final WhatsAppMessageSender whatsappMessageSender;

    // ==========================================
    // 📌 CONSTRUCTOR
    // ==========================================
    public AdminTicketService(TicketRepository ticketRepository,
                              StaffRepository staffRepository,
                              ChatSessionRepository chatSessionRepository,
                              WhatsAppMessageSender whatsappMessageSender) {
        this.ticketRepository = ticketRepository;
        this.staffRepository = staffRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.whatsappMessageSender = whatsappMessageSender;
    }

    // ==========================================
    // 👥 STAFF MANAGEMENT
    // ==========================================
    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    @Transactional
    public Staff addNewStaff(Staff staff) {
        staff.setCreatedAt(LocalDateTime.now());
        Staff savedStaff = staffRepository.save(staff);

        String welcomeMsg = formatMessage(
                "🎉 *WELCOME TO CA COMMAND CENTER*",
                "Hello *" + staff.getName() + "*, you have been successfully registered as a Staff Member.",
                "",
                "📋 *Your Responsibilities:*",
                "• Process client files assigned by Admin",
                "• Communicate with clients if documents are missing",
                "• Submit completed work for Quality Check",
                "",
                "💡 *Important Commands:*",
                "• Type *STATUS* - View your active files",
                "• Type *HELP* - Get command list",
                "",
                "You will receive WhatsApp notifications whenever Admin assigns a new client file to you. Stay ready! 💼"
        );

        whatsappMessageSender.sendMessage(savedStaff.getPhoneNumber(), welcomeMsg);
        System.out.println("✅ New staff added: " + staff.getName() + " (" + staff.getPhoneNumber() + ")");

        return savedStaff;
    }



    public Optional<Staff> getStaffById(UUID staffId) {
        return staffRepository.findById(staffId);
    }

    public void removeStaff(UUID staffId) {
        staffRepository.deleteById(staffId);
        System.out.println("🗑️ Staff removed: " + staffId);
    }

    // ==========================================
    // 📊 TICKET FETCHING (Read-Only Operations)
    // ==========================================
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public List<Ticket> getPendingTickets() {
        return ticketRepository.findByStatus("PENDING_ADMIN_APPROVAL");
    }

    public List<Ticket> getInProgressTickets() {
        return ticketRepository.findByStatus("IN_PROGRESS")
                .stream()
                .filter(t -> t.getAssignedStaff() != null)
                .collect(Collectors.toList());
    }

    public List<Ticket> getAwaitingPaymentTickets() {
        return ticketRepository.findByStatus("AWAITING_PAYMENT");
    }

    public List<Ticket> getQCPendingTickets() {
        return ticketRepository.findByStatus("PENDING_ADMIN_QC");
    }
    public List<Ticket> getCompletedTickets() {
        // 'FINISHED' ko change karke 'COMPLETED' kar diya
        return ticketRepository.findByStatus("COMPLETED");
    }

    public List<Ticket> getInProgressTicketsForStaff(UUID staffId) {
        return ticketRepository.findAll().stream()
                .filter(t -> t.getAssignedStaff() != null
                        && t.getAssignedStaff().getId().equals(staffId)
                        && ("ASSIGNED_TO_STAFF".equals(t.getStatus())
                        || "PENDING_ADMIN_QC".equals(t.getStatus())))
                .collect(Collectors.toList());
    }

    public Optional<Ticket> getTicketById(UUID ticketId) {
        return ticketRepository.findById(ticketId);
    }

    // ==========================================
    // ✅ TICKET APPROVAL & FEE SETTING
    // ==========================================
    @Transactional
    public Ticket approveTicketAndSetFee(UUID ticketId, com.caCommand.caCommand.dtos.AdminApprovalRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("❌ Ticket not found: " + ticketId));

        if (!"PENDING_ADMIN_APPROVAL".equals(ticket.getStatus())) {
            throw new RuntimeException("⚠️ Ticket is not pending approval. Current status: " + ticket.getStatus());
        }

        ticket.setStatus("AWAITING_PAYMENT");
        ticket.setQuotedFee(request.getFeeAmount().doubleValue());
        ticket.setUpdatedAt(LocalDateTime.now());

        Ticket updatedTicket = ticketRepository.save(ticket);

        // 📱 Send payment request to client
        String clientMsg = formatMessage(
                "✅ *DOCUMENTS VERIFIED & APPROVED*",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "Namaste! Your documents have been verified by our CA team.",
                "",
                "📋 *Service:* " + ticket.getServiceType(),
                "💰 *Quoted Fee:* ₹" + String.format("%.2f", request.getFeeAmount()),
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "💳 *PAYMENT METHODS:*",
                "",
                "1️⃣ *PhonePe/GPay/Paytm:*",
                "Scan this QR Code:",
                "🔗 https://dummyimage.com/300x300/4CAF50/fff&text=PhonePe+QR",
                "",
                "2️⃣ *Bank Transfer:*",
                "• A/C: 1234567890",
                "• IFSC: SBIN0001234",
                "• Name: CA Command Services",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "⚡ *After Payment:*",
                "Reply with *'PAID'* or send payment screenshot.",
                "",
                "We will verify and start processing your file immediately! 🚀"
        );

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), clientMsg);

        System.out.println("💰 Fee set for Ticket " + ticketId + ": ₹" + request.getFeeAmount());

        return updatedTicket;
    }

    // Legacy method support (backward compatibility)
    public Ticket approveTicket(UUID ticketId, Integer feeAmount) {
        com.caCommand.caCommand.dtos.AdminApprovalRequest request =
                new com.caCommand.caCommand.dtos.AdminApprovalRequest();
        request.setFeeAmount(feeAmount.doubleValue());
        return approveTicketAndSetFee(ticketId, request);
    }

    // ==========================================
    // 💳 PAYMENT VERIFICATION
    // ==========================================
    @Transactional
    public Ticket markPaymentSuccessful(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("❌ Ticket not found: " + ticketId));

        // 🌟 NAYA STATUS CHECK
        if (!"PAYMENT_RECEIVED".equals(ticket.getStatus()) && !"AWAITING_PAYMENT".equals(ticket.getStatus())) {
            throw new RuntimeException("⚠️ Ticket is not awaiting payment. Current status: " + ticket.getStatus());
        }

        ticket.setStatus("IN_PROGRESS");
        ticket.setUpdatedAt(LocalDateTime.now());

        Ticket updatedTicket = ticketRepository.save(ticket);

        String clientMsg = formatMessage(
                "🎉 *PAYMENT VERIFIED SUCCESSFULLY*",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "Thank you for the payment!",
                "",
                "✅ Amount Received: ₹" + String.format("%.2f", ticket.getQuotedFee()),
                "📋 Service: " + ticket.getServiceType(),
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "👨‍💼 *NEXT STEPS:*",
                "Our expert CA team has officially started working on your file.",
                "",
                "You will receive updates at key milestones.",
                "Expected completion: 3-5 business days",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "💬 Need help? Reply anytime - we're here! 🙌"
        );

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), clientMsg);

        System.out.println("✅ Payment verified for Ticket: " + ticketId);

        return updatedTicket;
    }

    // Legacy support
    public Ticket verifyPayment(UUID ticketId) {
        return markPaymentSuccessful(ticketId);
    }

    // ==========================================
    // 👷 STAFF ASSIGNMENT
    // ==========================================
    @Transactional
    public Ticket assignToStaff(UUID ticketId, UUID staffId, String priority, String notes) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("❌ Ticket not found: " + ticketId));

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("❌ Staff not found: " + staffId));

        if (!"IN_PROGRESS".equals(ticket.getStatus())) {
            throw new RuntimeException("⚠️ Ticket must be IN_PROGRESS before assignment. Current: " + ticket.getStatus());
        }

        ticket.setAssignedStaff(staff);
        ticket.setStatus("ASSIGNED_TO_STAFF");
        ticket.setPriority(priority != null ? priority : "MEDIUM");
        ticket.setAdminNotes(notes);
        ticket.setUpdatedAt(LocalDateTime.now());

        Ticket updatedTicket = ticketRepository.save(ticket);

        // 📱 Notify client about assignment
        String clientNotice = formatMessage(
                "💼 *YOUR FILE HAS BEEN ASSIGNED*",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "Good news! Our expert *" + staff.getName() + "* is now handling your case.",
                "",
                "📋 Service: " + ticket.getServiceType(),
                "⏰ Status: Work in Progress",
                "",
                "They may contact you directly if any additional documents are needed.",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "💡 Sit back and relax - we've got this! ✨"
        );
        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), clientNotice);

        // 📱 Send detailed work assignment to staff
        String documentsSection = formatDocumentList(ticket.getClientDocuments());

        String staffMsg = formatMessage(
                "💼 *NEW CLIENT FILE ASSIGNED*",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "📌 *TASK DETAILS:*",
                "• *Service:* 🛠️ " + ticket.getServiceType(),
                "• *Priority:* " + getPriorityEmoji(priority) + " " + priority.toUpperCase(),
                "• *Status:* ⏳ " + ticket.getStatus().replace("_", " "),
                "• *Fee Charged:* ₹" + String.format("%.2f", ticket.getQuotedFee()),
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "👤 *CLIENT INFO:*",
                "• *Phone:* " + ticket.getClient().getPhoneNumber(),
                "  (Direct WhatsApp: https://wa.me/" + ticket.getClient().getPhoneNumber() + ")",
                "• *City:* 📍 " + (ticket.getClient().getCity() != null ? ticket.getClient().getCity() : "Not provided"),
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "📂 *CLIENT DOCUMENTS:*",
                documentsSection,
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "📝 *ADMIN INSTRUCTIONS:*",
                notes != null && !notes.trim().isEmpty()
                        ? "👉 " + notes
                        : "✅ Process according to standard guidelines",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "🤖 *QUICK COMMANDS:*",
                "• *STATUS* - Refresh file status",
                "• *REQUEST: [Doc Name]* - Ask client for missing document",
                "• *QUERY: [Question]* - Ask client a question",
                "• *SUBMIT* - Upload final work for QC",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "⚡ Start working now! Time is ticking ⏰"
        );

        whatsappMessageSender.sendMessage(staff.getPhoneNumber(), staffMsg);

        System.out.println("👷 Ticket " + ticketId + " assigned to " + staff.getName());

        return updatedTicket;
    }

    // ==========================================
    // 📤 STAFF WORK SUBMISSION FOR QC
    // ==========================================
    @Transactional
    public Ticket submitWorkForQC(UUID ticketId, String documentUrl) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("❌ Ticket not found: " + ticketId));

        if (!"ASSIGNED_TO_STAFF".equals(ticket.getStatus())) {
            throw new RuntimeException("⚠️ Only assigned tickets can submit work. Current: " + ticket.getStatus());
        }

        ticket.setStaffSubmittedDocument(documentUrl);
        ticket.setStatus("PENDING_ADMIN_QC");
        ticket.setUpdatedAt(LocalDateTime.now());

        Ticket updatedTicket = ticketRepository.save(ticket);

        System.out.println("📤 Work submitted for QC - Ticket: " + ticketId);

        return updatedTicket;
    }

    // ==========================================
    // 🎉 FINAL DELIVERY & TICKET CLOSURE
    // ==========================================
    @Transactional
    public Ticket approveAndDeliver(UUID ticketId, String closingMessage) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("❌ Ticket not found: " + ticketId));

        if (!"PENDING_ADMIN_QC".equals(ticket.getStatus()) && !"ASSIGNED_TO_STAFF".equals(ticket.getStatus())) {
            throw new RuntimeException("⚠️ Ticket not ready for delivery. Current: " + ticket.getStatus());
        }

        ticket.setStatus("FINISHED");
        ticket.setUpdatedAt(LocalDateTime.now());
        ticket.setCompletedAt(LocalDateTime.now());

        Ticket finishedTicket = ticketRepository.save(ticket);

        // 📱 Send completion message to client
        String clientFinalMessage = formatMessage(
                "🎉 *WORK SUCCESSFULLY COMPLETED*",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "Congratulations! Your work is ready! 🎊",
                "",
                "📋 *Service:* " + ticket.getServiceType(),
                "✅ *Status:* Completed & Delivered",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "💬 *Message from CA Team:*",
                closingMessage != null ? closingMessage : "Your work has been completed as per requirements.",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "📎 *YOUR FINAL DOCUMENTS:*",
                ticket.getStaffSubmittedDocument() != null
                        ? "🔗 " + ticket.getStaffSubmittedDocument()
                        : "❌ No document link available (contact support)",
                "",
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                "⭐ *FEEDBACK REQUEST:*",
                "How was your experience? Reply with:",
                "• *EXCELLENT* ⭐⭐⭐⭐⭐",
                "• *GOOD* ⭐⭐⭐⭐",
                "• *AVERAGE* ⭐⭐⭐",
                "",
                "Thank you for choosing CA Command! 🙏",
                "Need more services? Just say *HI* anytime! 😊"
        );

        whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), clientFinalMessage);

        // 📱 Appreciate staff member
        if (ticket.getAssignedStaff() != null) {
            String staffAppreciation = formatMessage(
                    "🌟 *TASK SUCCESSFULLY CLOSED*",
                    "━━━━━━━━━━━━━━━━━━━━━━━━",
                    "Excellent work, *" + ticket.getAssignedStaff().getName() + "*! 👏",
                    "",
                    "✅ The ticket for client *" + ticket.getClient().getPhoneNumber() + "* has been successfully delivered and closed.",
                    "",
                    "📋 Service: " + ticket.getServiceType(),
                    "💰 Fee: ₹" + String.format("%.2f", ticket.getQuotedFee()),
                    "",
                    "━━━━━━━━━━━━━━━━━━━━━━━━",
                    "Keep up the great work! 💪",
                    "Ready for the next challenge? 🚀"
            );
            whatsappMessageSender.sendMessage(ticket.getAssignedStaff().getPhoneNumber(), staffAppreciation);
        }

        // 🧹 Clear chat session
        chatSessionRepository.findById(ticket.getClient().getPhoneNumber()).ifPresent(session -> {
            chatSessionRepository.delete(session);
            System.out.println("🧹 Chat session cleared for: " + ticket.getClient().getPhoneNumber());
        });

        System.out.println("🎉 Ticket completed: " + ticketId);

        return finishedTicket;
    }

    // Legacy support
    public Ticket completeTicketAndDeliver(UUID ticketId, String finalDocumentUrl, String closingMessage) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("❌ Ticket not found"));

        if (finalDocumentUrl != null && !finalDocumentUrl.trim().isEmpty()) {
            ticket.setStaffSubmittedDocument(finalDocumentUrl);
            ticketRepository.save(ticket);
        }

        return approveAndDeliver(ticketId, closingMessage);
    }

    // ==========================================
    // 🛠️ UTILITY HELPER METHODS
    // ==========================================

    /**
     * Format message with proper line breaks
     */
    private String formatMessage(String... lines) {
        return String.join("\n", lines);
    }

    /**
     * Format document list for display
     */
    private String formatDocumentList(String documentsString) {
        if (documentsString == null || documentsString.trim().isEmpty()) {
            return "❌ No documents uploaded yet";
        }

        String[] docs = documentsString.split(",");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < docs.length; i++) {
            String doc = docs[i].trim();
            formatted.append((i + 1)).append(". 🔗 ").append(doc);
            if (i < docs.length - 1) {
                formatted.append("\n");
            }
        }

        return formatted.toString();
    }

    /**
     * Get emoji for priority level
     */
    private String getPriorityEmoji(String priority) {
        if (priority == null) return "📌";

        return switch (priority.toUpperCase()) {
            case "HIGH", "URGENT" -> "🚨";
            case "MEDIUM" -> "⚡";
            case "LOW" -> "📌";
            default -> "📋";
        };
    }

    /**
     * Get ticket statistics for dashboard
     */
    public Map<String, Long> getTicketStatistics() {
        List<Ticket> allTickets = ticketRepository.findAll();

        return Map.of(
                "total", (long) allTickets.size(),
                "pending", allTickets.stream().filter(t -> "PENDING_ADMIN_APPROVAL".equals(t.getStatus())).count(),
                "awaiting_payment", allTickets.stream().filter(t -> "AWAITING_PAYMENT".equals(t.getStatus())).count(),
                "in_progress", allTickets.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus()) || "ASSIGNED_TO_STAFF".equals(t.getStatus())).count(),
                "pending_qc", allTickets.stream().filter(t -> "PENDING_ADMIN_QC".equals(t.getStatus())).count(),
                "completed", allTickets.stream().filter(t -> "FINISHED".equals(t.getStatus())).count()
        );
    }
}