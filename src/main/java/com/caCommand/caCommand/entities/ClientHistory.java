package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_history")
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class ClientHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(nullable = false, length = 100)
    private String serviceType;

    private Double feeCharged;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_ca_id")
    private Staff assignedCa;

    @Column(columnDefinition = "TEXT")
    private String finalSummary;

    @Column(length = 50)
    private String riskScore;

    @CreationTimestamp
    private LocalDateTime completionDate;
}
