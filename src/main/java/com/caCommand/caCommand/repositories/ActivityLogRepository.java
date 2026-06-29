package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
