package com.caCommand.caCommand.jobs;

import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.TicketStatus;
import com.caCommand.caCommand.repositories.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TicketCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TicketCleanupJob.class);
    // Tickets unpaid for more than 72 hours are moved to trash
    private static final long PAYMENT_TIMEOUT_HOURS = 72;

    private final TicketRepository ticketRepository;

    public TicketCleanupJob(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // Run once per day at 2am
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupUnpaidTickets() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(PAYMENT_TIMEOUT_HOURS);
        List<Ticket> expiredTickets = ticketRepository.findByStatusAndUpdatedAtBefore(
                TicketStatus.AWAITING_PAYMENT.name(),
                cutoffTime
        );

        for (Ticket ticket : expiredTickets) {
            ticket.setStatus(TicketStatus.TRASH.name());
            ticketRepository.save(ticket);
            log.info("Moved unpaid ticket id={} to trash after {}h payment timeout", ticket.getId(), PAYMENT_TIMEOUT_HOURS);
        }

        if (!expiredTickets.isEmpty()) {
            log.info("Cleanup complete: {} tickets moved to TRASH", expiredTickets.size());
        }
    }
}
