package com.caCommand.caCommand.jobs;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.services.StaffService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AttendanceReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReminderJob.class);

    private final StaffService staffService;
    private final WhatsAppMessageSender whatsappMessageSender;

    public AttendanceReminderJob(StaffService staffService, WhatsAppMessageSender whatsappMessageSender) {
        this.staffService = staffService;
        this.whatsappMessageSender = whatsappMessageSender;
    }

    // Run every day at 10:00 AM. Assuming server is in IST time zone.
    // Cron expression: seconds minutes hours day-of-month month day-of-week
    @Scheduled(cron = "0 30 10 * * MON-SAT", zone = "Asia/Kolkata")
    public void sendAttendanceReminders() {
        log.info("Starting Daily Attendance Reminder Job...");
        List<Staff> activeStaff = staffService.getAllStaff().stream()
                .filter(Staff::getIsActive)
                .toList();

        for (Staff staff : activeStaff) {
            String message = String.format("Hello %s, 👋\n\n" +
                    "Please upload your photo to mark today's attendance. 📸\n\n" +
                    "If you are not coming to the office today, simply reply with *NO* so that we can inform the Admin. 🛑", 
                    staff.getName());

            try {
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), message);
                log.info("Sent attendance reminder to staff {}", staff.getId());
            } catch (Exception e) {
                log.error("Failed to send attendance reminder to staff {}: {}", staff.getId(), e.getMessage());
            }
        }
        log.info("Finished Daily Attendance Reminder Job.");
    }
}
