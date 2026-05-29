package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    
    // Spring Boot automatically isko SQL query mein convert kar dega!
    List<Ticket> findByStatus(String status);
    List<Ticket> findByStatusAndUpdatedAtBefore(String status, LocalDateTime timeLimit);
}