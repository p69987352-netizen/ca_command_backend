package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByTicketIdOrderByTimestampAsc(UUID ticketId);
    void deleteByTicketId(UUID ticketId);
}
