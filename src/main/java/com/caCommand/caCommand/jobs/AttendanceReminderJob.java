package com.caCommand.caCommand.jobs;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Attendance;
import com.caCommand.caCommand.enums.AttendanceStatus;
import com.caCommand.caCommand.repositories.AttendanceRepository;
import com.caCommand.caCommand.services.StaffService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Component
public class AttendanceReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReminderJob.class);

    private final StaffService staffService;
    private final WhatsAppMessageSender whatsappMessageSender;
    private final AttendanceRepository attendanceRepository;

    public AttendanceReminderJob(
            StaffService staffService, 
            WhatsAppMessageSender whatsappMessageSender,
            AttendanceRepository attendanceRepository
    ) {
        this.staffService = staffService;
        this.whatsappMessageSender = whatsappMessageSender;
        this.attendanceRepository = attendanceRepository;
    }

    // Run Monday to Saturday at 10:30 AM.
    // Cron expression: seconds minutes hours day-of-month month day-of-week
    @Scheduled(cron = "0 30 10 * * MON-SAT", zone = "Asia/Kolkata")
    public void sendAttendanceReminders() {
        log.info("Starting Daily Attendance Reminder Job...");
        List<Staff> activeStaff = staffService.getAllStaff().stream()
                .filter(Staff::getIsActive)
                .toList();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        for (Staff staff : activeStaff) {
            Optional<Attendance> attOpt = attendanceRepository.findByStaffAndAttendanceDate(staff, today);
            boolean marked = attOpt.isPresent() && attOpt.get().getStatus() != AttendanceStatus.NOT_MARKED;
            
            if (marked) {
                log.info("Skipping attendance reminder for staff {} as attendance is already marked today.", staff.getName());
                continue;
            }

            String message = String.format("🚩 *Jai Shree Ram* 🚩\n\n" +
                    "Greetings,\n\n" +
                    "📖 *%s*\n\n" +
                    "Please mark your attendance by sending a photo. 📸\n\n" +
                    "If you are not coming to the office today, reply with *NO <reason>* (e.g. *NO sick*). 🛑", 
                    getRandomGitaQuote());

            try {
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), message);
                log.info("Sent attendance reminder to staff {}", staff.getId());
            } catch (Exception e) {
                log.error("Failed to send attendance reminder to staff {}: {}", staff.getId(), e.getMessage());
            }
        }
        log.info("Finished Daily Attendance Reminder Job.");
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
}
