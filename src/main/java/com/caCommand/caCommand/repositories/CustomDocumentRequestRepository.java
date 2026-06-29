package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.CustomDocumentRequest;
import com.caCommand.caCommand.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomDocumentRequestRepository extends JpaRepository<CustomDocumentRequest, String> {
    List<CustomDocumentRequest> findByTicketId(java.util.UUID ticketId);
    List<CustomDocumentRequest> findByTicketIdAndStatus(java.util.UUID ticketId, String status);
    List<CustomDocumentRequest> findByTicket(Ticket ticket);
}
