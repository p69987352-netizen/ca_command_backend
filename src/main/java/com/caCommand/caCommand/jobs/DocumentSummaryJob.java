package com.caCommand.caCommand.jobs;

import com.caCommand.caCommand.entities.ChatSession;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.repositories.ChatSessionRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.services.ChatBotService;
import com.caCommand.caCommand.services.GeminiService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentSummaryJob {

    private final TicketRepository ticketRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatBotService chatBotService;
    private final GeminiService geminiService;
    private final WhatsAppMessageSender whatsappMessageSender;

    @org.springframework.transaction.annotation.Transactional
    public void processPendingDocumentSummaries() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(5);
        List<Ticket> pendingTickets = ticketRepository.findByPendingDocumentSummaryTrueAndLastDocumentUploadedAtBefore(threshold);

        for (Ticket ticket : pendingTickets) {
            try {
                ChatSession session = chatSessionRepository.findById(ticket.getClient().getPhoneNumber()).orElse(null);
                if (session == null) {
                    ticket.setPendingDocumentSummary(false);
                    ticketRepository.save(ticket);
                    continue;
                }

                List<String> missingDocs = chatBotService.missingDocuments(ticket, session);
                int totalRequired = geminiService.getRequiredDocuments(ticket.getServiceType()).size();
                int missingCount = missingDocs.size();
                int completion = totalRequired == 0 ? 100 : (int) (((totalRequired - missingCount) * 100.0) / totalRequired);

                StringBuilder response = new StringBuilder("📊 *Document Status*\n\n");
                
                List<String> receivedDocs = java.util.Arrays.asList(
                    (session.getVerifiedDocumentTypes() == null ? "" : session.getVerifiedDocumentTypes()).split(",")
                ).stream().filter(s -> !s.isEmpty()).toList();
                
                if (!receivedDocs.isEmpty()) {
                    response.append("*Received:*\n");
                    for (String doc : receivedDocs) {
                        response.append("✓ ").append(doc).append("\n");
                    }
                    response.append("\n");
                }

                if (!missingDocs.isEmpty()) {
                    response.append("*Missing:*\n");
                    for (String doc : missingDocs) {
                        response.append("⚠ ").append(doc).append("\n");
                    }
                    response.append("\n");
                }

                response.append("*Document Completion:*\n").append(completion).append("%\n");

                if (missingDocs.isEmpty()) {
                    response.append("\n🎉 All required documents have been received! Our team is processing your file.");
                }

                whatsappMessageSender.sendMessage(ticket.getClient().getPhoneNumber(), response.toString());

                ticket.setPendingDocumentSummary(false);
                ticketRepository.save(ticket);

            } catch (Exception e) {
                log.error("Error processing document summary for ticket: {}", ticket.getId(), e);
                ticket.setPendingDocumentSummary(false); // prevent infinite loop
                ticketRepository.save(ticket);
            }
        }
    }
}
