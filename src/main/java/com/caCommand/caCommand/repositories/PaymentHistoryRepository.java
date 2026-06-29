package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {
    List<PaymentHistory> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<PaymentHistory> findByTicketId(UUID ticketId);
}
