package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.ExtractedData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExtractedDataRepository extends JpaRepository<ExtractedData, UUID> {
    Optional<ExtractedData> findFirstByClientIdOrderByCreatedAtDesc(UUID clientId);
}
