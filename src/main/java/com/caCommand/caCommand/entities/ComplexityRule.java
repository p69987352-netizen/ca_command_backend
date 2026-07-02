package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "complexity_rules")
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class ComplexityRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String code; // e.g. CAPITAL_GAIN

    @Column(nullable = false, length = 150)
    private String displayName; // e.g. Capital Gain Analysis

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private Integer priority;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean isEnabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
