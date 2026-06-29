package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.ClientHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface ClientHistoryRepository extends JpaRepository<ClientHistory, UUID> {
    List<ClientHistory> findByClientIdOrderByCompletionDateDesc(UUID clientId);
}
