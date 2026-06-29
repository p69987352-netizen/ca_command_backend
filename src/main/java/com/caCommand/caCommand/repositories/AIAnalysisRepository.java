package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.AIAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, UUID> {
    Optional<AIAnalysis> findFirstByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}
