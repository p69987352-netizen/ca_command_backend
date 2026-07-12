package com.caCommand.caCommand.jobs;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Attendance;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.AttendanceStatus;
import com.caCommand.caCommand.repositories.AttendanceRepository;
import com.caCommand.caCommand.repositories.StaffRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.services.WhatsAppMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class WorkloadReportJob {

    private static final Logger log = LoggerFactory.getLogger(WorkloadReportJob.class);

    private final StaffRepository staffRepository;
    private final AttendanceRepository attendanceRepository;
    private final TicketRepository ticketRepository;
    private final WhatsAppMessageSender whatsappMessageSender;
    private final String adminPhoneNumber;

    public WorkloadReportJob(
            StaffRepository staffRepository,
            AttendanceRepository attendanceRepository,
            TicketRepository ticketRepository,
            WhatsAppMessageSender whatsappMessageSender,
            @Value("${whatsapp.admin-phone-number}") String adminPhoneNumber
    ) {
        this.staffRepository = staffRepository;
        this.attendanceRepository = attendanceRepository;
        this.ticketRepository = ticketRepository;
        this.whatsappMessageSender = whatsappMessageSender;
        this.adminPhoneNumber = adminPhoneNumber;
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Kolkata")
    public void sendWorkloadReport() {
        log.info("Starting Daily Workload and Attendance EOD Report Job...");
        if (adminPhoneNumber == null || adminPhoneNumber.isBlank()) {
            log.warn("Admin phone number is not configured. Skipping report.");
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<Staff> activeStaff = staffRepository.findAll().stream()
                .filter(Staff::getIsActive)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 *EOD REPORT - %s*\n\n", today.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))));

        java.util.List<String> presentLines = new java.util.ArrayList<>();
        java.util.List<String> absentLines = new java.util.ArrayList<>();
        java.util.List<String> notMarkedNames = new java.util.ArrayList<>();

        for (Staff staff : activeStaff) {
            Optional<Attendance> attOpt = attendanceRepository.findByStaffAndAttendanceDate(staff, today);
            if (attOpt.isPresent()) {
                Attendance att = attOpt.get();
                if (att.getStatus() == AttendanceStatus.PRESENT) {
                    String checkinTime = att.getCreatedAt() != null ? att.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("hh:mm a")) : "-";
                    String checkoutTime = att.getExitTime() != null ? att.getExitTime().atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("hh:mm a")) : "Not Checked Out";
                    
                    StringBuilder pBuilder = new StringBuilder();
                    pBuilder.append(String.format("• *%s* (In: %s | Out: %s)", staff.getName(), checkinTime, checkoutTime));
                    if (att.getLocationLink() != null) {
                        pBuilder.append("\n  📍 *Location:* ").append(att.getLocationLink());
                    }
                    if (att.getExitLocationLink() != null) {
                        pBuilder.append("\n  📍 *Exit Location:* ").append(att.getExitLocationLink());
                    }
                    presentLines.add(pBuilder.toString());
                } else if (att.getStatus() == AttendanceStatus.ABSENT) {
                    absentLines.add(String.format("• *%s*\n  ↳ Reason: %s", staff.getName(), att.getReason() != null ? att.getReason() : "No reason provided"));
                } else {
                    notMarkedNames.add(staff.getName());
                }
            } else {
                notMarkedNames.add(staff.getName());
            }
        }

        // 1. Attendance Details
        sb.append("🟢 *PRESENT STAFF:*\n");
        if (presentLines.isEmpty()) {
            sb.append("None\n");
        } else {
            for (String line : presentLines) {
                sb.append(line).append("\n");
            }
        }
        sb.append("\n");

        sb.append("🔴 *ABSENT STAFF:*\n");
        if (absentLines.isEmpty()) {
            sb.append("None\n");
        } else {
            for (String line : absentLines) {
                sb.append(line).append("\n");
            }
        }
        sb.append("\n");

        sb.append("🟡 *PENDING (NOT MARKED):*\n");
        if (notMarkedNames.isEmpty()) {
            sb.append("None\n");
        } else {
            sb.append(String.join(", ", notMarkedNames)).append("\n");
        }
        sb.append("\n");

        // 2. Pending Workload Count
        sb.append("📋 *PENDING WORKLOAD (BY STAFF):*\n");
        List<String> activeStatuses = List.of("ASSIGNED_TO_STAFF", "PENDING_ADMIN_QC", "CALL_PENDING");
        boolean anyWorkload = false;
        for (Staff staff : activeStaff) {
            List<Ticket> activeTickets = ticketRepository.findByAssignedStaffIdAndStatusIn(staff.getId(), activeStatuses);
            if (!activeTickets.isEmpty()) {
                anyWorkload = true;
                sb.append(String.format("• *%s*: %d pending case(s)\n", staff.getName(), activeTickets.size()));
            }
        }
        if (!anyWorkload) {
            sb.append("No active tickets pending.\n");
        }
        sb.append("\n");

        // 3. Completed Today
        sb.append("✅ *TASKS COMPLETED TODAY:*\n");
        List<Ticket> finishedTickets = ticketRepository.findByStatus("FINISHED");
        List<Ticket> completedTickets = ticketRepository.findByStatus("COMPLETED");
        List<Ticket> allFinished = new java.util.ArrayList<>();
        allFinished.addAll(finishedTickets);
        allFinished.addAll(completedTickets);

        long completedTodayCount = 0;
        for (Ticket t : allFinished) {
            java.time.LocalDateTime timeToCheck = t.getCompletedAt() != null ? t.getCompletedAt() : t.getUpdatedAt();
            if (timeToCheck != null) {
                java.time.LocalDate dateInKolkata = timeToCheck
                        .atZone(java.time.ZoneId.systemDefault())
                        .withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"))
                        .toLocalDate();
                if (dateInKolkata.isEqual(today)) {
                    completedTodayCount++;
                    String staffName = t.getAssignedStaff() != null ? t.getAssignedStaff().getName() : "Unassigned";
                    sb.append(String.format("• *%s* | %s (Staff: %s)\n", t.getCaseId(), t.getServiceType(), staffName));
                }
            }
        }
        if (completedTodayCount == 0) {
            sb.append("No cases completed today.\n");
        }

        this.whatsappMessageSender.sendMessage(adminPhoneNumber, sb.toString());
        log.info("EOD Report sent to admin successfully.");
    }
}
