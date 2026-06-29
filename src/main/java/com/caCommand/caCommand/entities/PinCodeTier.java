package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps Indian PIN codes to city tiers for pricing.
 * Tier A = Metro (no discount)
 * Tier B = Medium city (10% off)
 * Tier C = Small city / rural (15% off)
 */
@Entity
@Table(name = "pin_code_tiers", indexes = {
        @Index(name = "idx_pincode", columnList = "pinCode")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper=false)
public class PinCodeTier extends BaseEntity {

    @Id
    @Column(length = 6)
    private String pinCode;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    /**
     * Tier: A (Metro) | B (Medium) | C (Small/Rural)
     */
    @Column(nullable = false, length = 1)
    private String tier;

    // Discount percent for this tier (0, 10, or 15)
    @Column(nullable = false)
    private Integer discountPercent;
}
