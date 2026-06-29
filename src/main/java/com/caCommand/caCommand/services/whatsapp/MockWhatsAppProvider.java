package com.caCommand.caCommand.services.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
public class MockWhatsAppProvider implements WhatsAppProvider {

    @Override
    public void sendMessage(String toPhoneNumber, String messageText) {
        log.info("[MOCK WHATSAPP] To: {} | Message: \n{}", toPhoneNumber, messageText);
    }

    @Override
    public void sendDocument(String toPhoneNumber, String documentUrl, String caption) {
        log.info("[MOCK WHATSAPP] To: {} | Document: {} | Caption: {}", toPhoneNumber, documentUrl, caption);
    }

    @Override
    public void sendInteractiveMenu(String toPhoneNumber, String header, String body, String[] options) {
        log.info("[MOCK WHATSAPP] To: {} | Menu: {} - {}\nOptions: {}", toPhoneNumber, header, body, Arrays.toString(options));
    }
}
