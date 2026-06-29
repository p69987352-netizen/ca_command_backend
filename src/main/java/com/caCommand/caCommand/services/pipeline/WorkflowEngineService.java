package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.PipelineStatus;
import com.caCommand.caCommand.enums.TicketStatus;
import com.caCommand.caCommand.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final TicketRepository ticketRepository;

    // VALID TRANSITIONS FOR PIPELINE (Technical Execution Flow)
    private static final Map<PipelineStatus, Set<PipelineStatus>> PIPELINE_TRANSITIONS = Map.ofEntries(
            Map.entry(PipelineStatus.IDLE, Set.of(PipelineStatus.QUEUED)),
            Map.entry(PipelineStatus.QUEUED, Set.of(PipelineStatus.LOCK_ACQUIRED, PipelineStatus.CANCELLED)),
            Map.entry(PipelineStatus.LOCK_ACQUIRED, Set.of(PipelineStatus.DOCUMENT_LOADING, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.DOCUMENT_LOADING, Set.of(PipelineStatus.VALIDATING, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.VALIDATING, Set.of(PipelineStatus.OCR_RUNNING, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.OCR_RUNNING, Set.of(PipelineStatus.CLASSIFICATION, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.CLASSIFICATION, Set.of(PipelineStatus.RULE_ENGINE, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.RULE_ENGINE, Set.of(PipelineStatus.AI_ANALYSIS, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.AI_ANALYSIS, Set.of(PipelineStatus.PRICING, PipelineStatus.SUCCESS, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.PRICING, Set.of(PipelineStatus.REPORT_GENERATION, PipelineStatus.SUCCESS, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.REPORT_GENERATION, Set.of(PipelineStatus.NOTIFICATION, PipelineStatus.SUCCESS, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.NOTIFICATION, Set.of(PipelineStatus.SUCCESS, PipelineStatus.FAILED)),
            Map.entry(PipelineStatus.SUCCESS, Set.of(PipelineStatus.IDLE, PipelineStatus.QUEUED)), // Allow restart on new doc
            Map.entry(PipelineStatus.FAILED, Set.of(PipelineStatus.IDLE, PipelineStatus.QUEUED)), // Allow retry
            Map.entry(PipelineStatus.CANCELLED, Set.of(PipelineStatus.IDLE, PipelineStatus.QUEUED))
    );

    // VALID TRANSITIONS FOR TICKET (Business Flow)
    private static final Map<TicketStatus, Set<TicketStatus>> TICKET_TRANSITIONS = Map.ofEntries(
            Map.entry(TicketStatus.NEW, Set.of(TicketStatus.WAITING_FOR_DOCUMENTS, TicketStatus.READY_FOR_PROCESSING)),
            Map.entry(TicketStatus.WAITING_FOR_DOCUMENTS, Set.of(TicketStatus.READY_FOR_PROCESSING)),
            Map.entry(TicketStatus.READY_FOR_PROCESSING, Set.of(TicketStatus.UNDER_REVIEW, TicketStatus.WAITING_FOR_DOCUMENTS)), // Pipeline updates business state
            Map.entry(TicketStatus.UNDER_REVIEW, Set.of(TicketStatus.PAYMENT_PENDING, TicketStatus.COMPLETED)),
            Map.entry(TicketStatus.PAYMENT_PENDING, Set.of(TicketStatus.PAYMENT_VERIFIED)),
            Map.entry(TicketStatus.PAYMENT_VERIFIED, Set.of(TicketStatus.CA_ASSIGNED, TicketStatus.COMPLETED)),
            Map.entry(TicketStatus.CA_ASSIGNED, Set.of(TicketStatus.COMPLETED)),
            Map.entry(TicketStatus.COMPLETED, Set.of(TicketStatus.ARCHIVED))
    );

    public boolean canTransitionPipeline(PipelineStatus currentStatus, PipelineStatus newStatus) {
        if (currentStatus == newStatus) return true;
        if (newStatus == PipelineStatus.FAILED || newStatus == PipelineStatus.CANCELLED) return true; // Global terminal states
        Set<PipelineStatus> allowed = PIPELINE_TRANSITIONS.get(currentStatus);
        return allowed != null && allowed.contains(newStatus);
    }

    public boolean canTransitionTicket(TicketStatus currentStatus, TicketStatus newStatus) {
        if (currentStatus == newStatus) return true;
        Set<TicketStatus> allowed = TICKET_TRANSITIONS.get(currentStatus);
        return allowed != null && allowed.contains(newStatus) || TicketStatus.isActive(newStatus.name()); // Legacy support fallback
    }

    @org.springframework.transaction.annotation.Transactional
    public void transition(Ticket ticket, PipelineStatus newStatus) {
        PipelineStatus currentStatus;
        try {
            currentStatus = PipelineStatus.valueOf(ticket.getPipelineStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            currentStatus = PipelineStatus.IDLE;
        }

        if (!canTransitionPipeline(currentStatus, newStatus)) {
            log.warn("Invalid Pipeline transition from {} to {}. Throwing exception to fail pipeline gracefully.", currentStatus, newStatus);
            throw new IllegalStateException("Invalid pipeline status transition from " + currentStatus + " to " + newStatus);
        }

        ticket.setPipelineStatus(newStatus.name());
        ticketRepository.updatePipelineStatus(ticket.getId(), newStatus.name());
        log.info("Pipeline status for ticket {} updated: {} -> {}", ticket.getId(), currentStatus, newStatus);
    }

    @org.springframework.transaction.annotation.Transactional
    public void transition(Ticket ticket, TicketStatus newStatus) {
        TicketStatus currentStatus = null;
        try {
            if (ticket.getStatus() != null) {
                currentStatus = TicketStatus.valueOf(ticket.getStatus());
            }
        } catch (IllegalArgumentException e) {
            log.warn("Ticket {} has an invalid or legacy status: {}", ticket.getId(), ticket.getStatus());
        }

        if (currentStatus != null && !canTransitionTicket(currentStatus, newStatus)) {
            log.warn("Invalid Ticket transition from {} to {}", currentStatus, newStatus);
            throw new IllegalStateException("Invalid ticket status transition from " + currentStatus + " to " + newStatus);
        }

        ticket.setStatus(newStatus.name());
        ticketRepository.updateBusinessStatus(ticket.getId(), newStatus.name());
        log.info("Business status for ticket {} updated: {} -> {}", ticket.getId(), currentStatus, newStatus);
    }
}
