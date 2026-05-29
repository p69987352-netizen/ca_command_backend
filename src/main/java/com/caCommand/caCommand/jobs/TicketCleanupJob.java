package com.caCommand.caCommand.jobs;

import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.repositories.TicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TicketCleanupJob {

    private final TicketRepository ticketRepository;

    public TicketCleanupJob(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // CRON format: (sec min hour day month weekday)
    // "0 0 * * * *" = Har ghante chalega
    // Lekin testing ke liye hum ise "0 * * * * *" (Har minute) chalaenge!
    @Scheduled(cron = "0 * * * * *") 
    public void cleanupUnpaidTickets() {
        System.out.println("⏳ [CRON JOB] Checking for expired unpaid tickets...");

        // REAL WORLD MEIN: LocalDateTime.now().minusHours(48)
        // TESTING KE LIYE: Hum 2 minute purani tickets ko hi trash kar denge
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(2); 

        List<Ticket> expiredTickets = ticketRepository.findByStatusAndUpdatedAtBefore("AWAITING_PAYMENT", cutoffTime);

        if (!expiredTickets.isEmpty()) {
            for (Ticket ticket : expiredTickets) {
                ticket.setStatus("TRASH"); // Ticket moved to Trash
                ticketRepository.save(ticket);
                
                System.out.println("🗑️ Ticket ID: " + ticket.getId() + " moved to TRASH due to payment timeout.");
                // Future: Send WhatsApp message "Your ticket was closed due to non-payment".
            }
        }
    }
}