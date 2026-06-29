package com.caCommand.caCommand.dtos;

import java.util.List;

public class GeminiAIResponse {

    private String name;       // Client's first name extracted from message
    private String city;
    private String service;
    private String reply_message;
    private List<String> documents_required;
    private String conversation_stage;

    // Constructors
    public GeminiAIResponse() {
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getReply_message() { return reply_message; }
    public void setReply_message(String reply_message) { this.reply_message = reply_message; }

    public List<String> getDocuments_required() { return documents_required; }
    public void setDocuments_required(List<String> documents_required) { this.documents_required = documents_required; }

    public String getConversation_stage() { return conversation_stage; }
    public void setConversation_stage(String conversation_stage) { this.conversation_stage = conversation_stage; }
}