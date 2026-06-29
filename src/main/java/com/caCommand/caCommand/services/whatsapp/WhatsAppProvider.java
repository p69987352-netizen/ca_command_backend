package com.caCommand.caCommand.services.whatsapp;

public interface WhatsAppProvider {
    void sendMessage(String toPhoneNumber, String messageText);
    void sendDocument(String toPhoneNumber, String documentUrl, String caption);
    void sendInteractiveMenu(String toPhoneNumber, String header, String body, String[] options);
}
