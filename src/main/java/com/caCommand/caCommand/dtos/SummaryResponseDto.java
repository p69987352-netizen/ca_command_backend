package com.caCommand.caCommand.dtos;

import com.caCommand.caCommand.entities.ActivityLog;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.ExtractedData;
import com.caCommand.caCommand.entities.AIAnalysis;
import com.caCommand.caCommand.entities.PaymentHistory;
import com.caCommand.caCommand.entities.ClientHistory;
import lombok.Data;

import java.util.List;

@Data
public class SummaryResponseDto {
    private Client clientProfile;
    private ExtractedData extractedData;
    private Integer readinessScore;
    private List<String> missingDocuments;
    private List<DocumentDto> receivedDocuments;
    private List<ActivityLog> activityTimeline;
    
    @Data
    public static class DocumentDto {
        private String name;
        private String url;
        
        public DocumentDto(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
    private String recommendedAction;
    private String latestTicketId;
    private String status;
    private Double feeQuoted;
    private Double adminFinalFee;
    private String paymentProofUrl;
    
    // New fields for Client 360 View
    private AIAnalysis aiAnalysis;
    private List<PaymentHistory> paymentHistory;
    private List<ClientHistory> previousCases;
    
    // Customer Intelligence
    private Double lifetimeRevenue;
    private Integer totalPreviousCases;
    private String lastServiceName;
    private String lastInteractionDate;
}