package com.caCommand.caCommand.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_sessions")
@Data
public class StaffSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private java.util.UUID staffId;
    
    private String activeCaseId;
    
    private String lastCommand;
    
    private LocalDateTime selectedAt;
    
    private LocalDateTime expiresAt;
}
