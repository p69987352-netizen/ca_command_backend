package com.caCommand.caCommand.events;

import org.springframework.context.ApplicationEvent;

public class DocumentUploadedEvent extends ApplicationEvent {
    private final String ticketId;
    private final String phoneNumber;

    public DocumentUploadedEvent(Object source, String ticketId, String phoneNumber) {
        super(source);
        this.ticketId = ticketId;
        this.phoneNumber = phoneNumber;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}
