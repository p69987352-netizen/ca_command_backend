package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staff_members")
@Data
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name; // e.g., "Keshav"

    @Column(nullable = false, unique = true)
    private String phoneNumber;



    // Staff ka WhatsApp number

    private LocalDateTime createdAt ;

}