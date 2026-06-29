package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "extracted_data", indexes = {
        @Index(name = "idx_extracted_client", columnList = "client_id")
})
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class ExtractedData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(length = 100)
    private String documentType;

    @Column(length = 20)
    private String assessmentYear;

    @Column(length = 20)
    private String financialYear;

    private Double salaryIncome;
    private Double interestIncome;
    private Double dividendIncome;
    private Double tds;

    private Double aisReportedIncome;
    private Double tisReportedIncome;
    private Double incomeDifference;
    private Double capitalGains;
    
    @Column(length = 20)
    private String suggestedItr;

    private Double tcs;
    private Double refundOpportunity;
    private Double demandOutstanding;

    // e.g. Low, Medium, High
    @Column(length = 20)
    private String riskScore;

    @Column(length = 50)
    private String noticeType;
    
    @Column(length = 50)
    private String section;

    private LocalDateTime dueDate;

    // To store any unstructured data that future documents might contain
    @Column(columnDefinition = "TEXT")
    private String rawJson;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
