package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves PDF passwords for password-protected documents like AIS/TIS.
 * 
 * Income Tax Portal Password Rule:
 * Password = PAN (lowercase) + DOB (DDMMYYYY)
 * Example: PAN=ABCDE1234F, DOB=15/08/1990 → abcde1234f15081990
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResolver {

    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;

    /**
     * Resolves the PDF password for a given ticket.
     * Pipeline calls this — Pipeline never touches ClientRepository directly.
     */
    public String resolvePassword(String ticketId, DocumentType docType) {
        if (!docType.requiresPassword()) {
            return null;
        }
        
        try {
            Ticket ticket = ticketRepository.findById(UUID.fromString(ticketId)).orElse(null);
            if (ticket == null || ticket.getClient() == null) {
                log.warn("Cannot resolve password: ticket or client not found for ticket={}", ticketId);
                return null;
            }
            
            Client client = clientRepository.findById(ticket.getClient().getId()).orElse(null);
            if (client == null) {
                log.warn("Cannot resolve password: client not found for ticket={}", ticketId);
                return null;
            }
            
            return generateAisPassword(client.getPan(), client.getDob());
        } catch (Exception e) {
            log.error("Error resolving password for ticket={}", ticketId, e);
            return null;
        }
    }

    /**
     * Generates AIS/TIS PDF password from PAN + DOB.
     * Rule: PAN (lowercase) + DOB (DDMMYYYY)
     */
    public String generateAisPassword(String pan, String dob) {
        if (pan == null || pan.isBlank() || dob == null || dob.isBlank()) {
            log.warn("Cannot generate AIS password: PAN or DOB is missing");
            return null;
        }
        
        String normalizedDob = normalizeDob(dob);
        if (normalizedDob == null) {
            log.warn("Cannot generate AIS password: DOB format not recognized: {}", dob);
            return null;
        }
        
        String password = pan.toLowerCase().trim() + normalizedDob;
        log.info("Generated AIS password for PAN: {}****{}", 
                pan.substring(0, Math.min(5, pan.length())), 
                pan.length() > 9 ? pan.substring(9) : "");
        return password;
    }

    /**
     * Smart DOB normalizer - handles multiple input formats.
     * Output: DDMMYYYY (e.g., "15081990")
     */
    String normalizeDob(String dob) {
        if (dob == null || dob.isBlank()) return null;
        String trimmed = dob.trim();
        
        // Already in DDMMYYYY format (8 digits)
        if (trimmed.matches("\\d{8}")) {
            return trimmed;
        }
        
        // Try common date formats
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),   // 15/08/1990
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),   // 15-08-1990
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),   // 15.08.1990
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),   // 1990-08-15 (ISO)
            DateTimeFormatter.ofPattern("dd MMM yyyy"),  // 15 Aug 1990
            DateTimeFormatter.ofPattern("dd MMMM yyyy"), // 15 August 1990
            DateTimeFormatter.ofPattern("d/M/yyyy"),     // 5/8/1990
            DateTimeFormatter.ofPattern("d-M-yyyy"),     // 5-8-1990
            DateTimeFormatter.ofPattern("MM/dd/yyyy")    // 08/15/1990 (US format fallback)
        );
        
        for (DateTimeFormatter fmt : formatters) {
            try {
                LocalDate date = LocalDate.parse(trimmed, fmt);
                return String.format("%02d%02d%04d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        
        // Last resort: extract digits if there are exactly 8
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        if (digitsOnly.length() == 8) {
            return digitsOnly;
        }
        
        log.warn("Could not parse DOB: '{}' - returning null", dob);
        return null;
    }
}
