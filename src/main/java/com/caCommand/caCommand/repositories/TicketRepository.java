package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Optional<Ticket> findByCaseId(String caseId);

    List<Ticket> findByStatus(String status);

    List<Ticket> findByStatusAndUpdatedAtBefore(String status, LocalDateTime timeLimit);

    List<Ticket> findByAssignedStaffIdAndStatusIn(UUID staffId, List<String> statuses);

    Optional<Ticket> findFirstByClientIdAndStatusInOrderByCreatedAtDesc(UUID clientId, List<String> statuses);

    long countByStatus(String status);

    @Query("SELECT t FROM Ticket t WHERE t.client.phoneNumber = :phoneNumber")
    List<Ticket> findByClientPhoneNumber(@Param("phoneNumber") String phoneNumber);

    // Full client history (all tickets), newest first
    @Query("SELECT t FROM Ticket t WHERE t.client.id = :clientId ORDER BY t.createdAt DESC")
    List<Ticket> findAllByClientIdOrderByCreatedAtDesc(@Param("clientId") UUID clientId);

    // History by phone
    @Query("SELECT t FROM Ticket t WHERE t.client.phoneNumber = :phoneNumber ORDER BY t.createdAt DESC")
    List<Ticket> findAllByClientPhoneOrderByCreatedAtDesc(@Param("phoneNumber") String phoneNumber);

    // Tickets with pending credentials
    @Query("SELECT t FROM Ticket t WHERE t.credentialStatus = 'REQUESTED'")
    List<Ticket> findAllWithCredentialRequested();

    // Count active tickets for a client
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.client.id = :clientId AND t.status NOT IN ('FINISHED','COMPLETED','TRASH')")
    long countActiveByClientId(@Param("clientId") UUID clientId);

    List<Ticket> findByStatusOrderByCreatedAtDesc(String status);
    
    List<Ticket> findByPendingDocumentSummaryTrueAndLastDocumentUploadedAtBefore(LocalDateTime timeLimit);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.pipelineStatus = :status WHERE t.id = :id")
    void updatePipelineStatus(@Param("id") UUID id, @Param("status") String status);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.status = :status WHERE t.id = :id")
    void updateBusinessStatus(@Param("id") UUID id, @Param("status") String status);
}
