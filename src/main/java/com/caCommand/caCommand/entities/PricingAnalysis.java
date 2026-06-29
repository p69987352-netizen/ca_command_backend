package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import com.caCommand.caCommand.enums.PricingTier;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_analysis")
@lombok.EqualsAndHashCode(callSuper=false)
public class PricingAnalysis extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String ticketId;

    private int complexityScore;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PricingTier pricingTier;
    
    private double recommendedFee;

    private Double competitorFee;

    @Column(length = 100)
    private String competitorName;

    private int confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String feeReasoning; // JSON array of reasons

    private double estimatedWorkHours;

    @Column(length = 100)
    private String serviceCategory;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public int getComplexityScore() { return complexityScore; }
    public void setComplexityScore(int complexityScore) { this.complexityScore = complexityScore; }

    public double getRecommendedFee() { return recommendedFee; }
    public void setRecommendedFee(double recommendedFee) { this.recommendedFee = recommendedFee; }

    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getFeeReasoning() { return feeReasoning; }
    public void setFeeReasoning(String feeReasoning) { this.feeReasoning = feeReasoning; }

    public double getEstimatedWorkHours() { return estimatedWorkHours; }
    public void setEstimatedWorkHours(double estimatedWorkHours) { this.estimatedWorkHours = estimatedWorkHours; }

    public String getServiceCategory() { return serviceCategory; }
    public void setServiceCategory(String serviceCategory) { this.serviceCategory = serviceCategory; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public PricingTier getPricingTier() { return pricingTier; }
    public void setPricingTier(PricingTier pricingTier) { this.pricingTier = pricingTier; }

    public Double getCompetitorFee() { return competitorFee; }
    public void setCompetitorFee(Double competitorFee) { this.competitorFee = competitorFee; }

    public String getCompetitorName() { return competitorName; }
    public void setCompetitorName(String competitorName) { this.competitorName = competitorName; }
}
