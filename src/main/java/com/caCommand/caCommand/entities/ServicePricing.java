package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pricing table for all CA services.
 * Card rate = full MRP price.
 * Tier A/B/C = discounted amounts based on PIN code tier.
 */
@Entity
@Table(name = "service_pricing")
@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper=false)
public class ServicePricing extends BaseEntity {

    @Id
    @Column(length = 60)
    private String serviceKey; // e.g. "ITR-1", "ITR-2", "GST_REGISTRATION"

    @Column(nullable = false, length = 120)
    private String displayName; // e.g. "ITR-1 Filing (Salaried)"

    @Column(columnDefinition = "TEXT")
    private String description; // Who should use this

    // Card Rates (full price)
    @Column(nullable = false)
    private Double cardRate;

    // Tier B = Medium cities (10% off card rate)
    @Column(nullable = false)
    private Double tierBRate;

    // Tier C = Small cities (15% off card rate)
    @Column(nullable = false)
    private Double tierCRate;

    // Additional discount % for income < 5L
    private Integer incomeBelowFiveLakhDiscount = 5;

    // For ITR: which ITR form is this
    @Column(length = 10)
    private String itrFormType;

    // Category: ITR / GST / AUDIT / COMPANY / PAN / TDS / OTHER
    @Column(length = 20)
    private String category;

    // Who should file this (displayed to admin/chatbot)
    @Column(columnDefinition = "TEXT")
    private String eligibility;

    private Boolean isActive = true;
}
