package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients", indexes = {
        @Index(name = "idx_clients_phone_number", columnList = "phoneNumber"),
        @Index(name = "idx_clients_name", columnList = "name")
})
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@lombok.EqualsAndHashCode(callSuper=false)
public class Client extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id")
    private Firm firm;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String name;

    @Column(length = 120)
    private String city;

    // PIN code for location-based pricing
    @Column(length = 6)
    private String pinCode;

    // Client type: SALARIED / BUSINESS / PROFESSIONAL / OTHER
    @Column(length = 30)
    private String clientType;

    // Annual income range: BELOW_5L / 5L_10L / 10L_25L / ABOVE_25L
    @Column(length = 20)
    private String incomeRange;

    @Column(length = 20)
    private String pan;

    @Column(length = 20)
    private String dob;

    @Column(length = 120)
    private String itPassword;

    // Analytics fields
    private Double totalRevenueGenerated = 0.0;
    private Double averageCaseValue = 0.0;
    private Integer totalCases = 0;
    private Integer completedCases = 0;

    // Total completed services
    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer totalServicesCompleted = 0;

    @Column
    private LocalDateTime lastServiceDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
