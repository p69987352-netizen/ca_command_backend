package com.caCommand.caCommand.events;

import com.caCommand.caCommand.services.notification.NotificationService;
import com.caCommand.caCommand.services.pipeline.PipelineOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentUploadedEventListener {

    private final PipelineOrchestrator pipelineOrchestrator;
    private final NotificationService notificationService;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    @EventListener
    @Async
    public void handleDocumentUploadedEvent(DocumentUploadedEvent event) {
        String ticketId = event.getTicketId();
        String phoneNumber = event.getPhoneNumber();
        
        log.info("Received DocumentUploadedEvent for ticket: {}", ticketId);

        // Debounce logic: Cancel existing scheduled task for this ticket if any
        ScheduledFuture<?> existingTask = pendingTasks.get(ticketId);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
            log.debug("Cancelled existing debounced task for ticket: {}", ticketId);
        }

        // Schedule new task after 5 seconds
        Runnable pipelineTask = () -> {
            try {
                log.info("Debounce window closed. Starting pipeline for ticket: {}", ticketId);
                
                // Trigger the pipeline for the entire ticket
                // (Silently, no WhatsApp message here)
                
                // Trigger the pipeline for the entire ticket
                pipelineOrchestrator.processTicketAsync(ticketId);
                
            } catch (Exception e) {
                log.error("Error starting pipeline for ticket: {}", ticketId, e);
            } finally {
                pendingTasks.remove(ticketId);
            }
        };

        ScheduledFuture<?> future = scheduler.schedule(pipelineTask, 5, TimeUnit.SECONDS);
        pendingTasks.put(ticketId, future);
    }
}
