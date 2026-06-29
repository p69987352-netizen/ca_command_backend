package com.caCommand.caCommand.repositories;
import com.caCommand.caCommand.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByClientIdAndReadStatusFalseOrderByCreatedAtDesc(UUID clientId);
}
