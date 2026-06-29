package com.caCommand.caCommand.services.notification;

import com.caCommand.caCommand.services.WhatsAppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final WhatsAppMessageSender whatsappMessageSender;

    public void sendWhatsAppMessage(String phoneNumber, String message) {
        if (phoneNumber == null || message == null || message.isBlank()) {
            return;
        }
        try {
            whatsappMessageSender.sendMessage(phoneNumber, message.strip());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to: {}", phoneNumber, e);
        }
    }

    public void sendSystemNotification(String adminId, String message) {
        // Future enhancement: Send email or internal dashboard notification
        log.info("System Notification for Admin {}: {}", adminId, message);
    }
}
