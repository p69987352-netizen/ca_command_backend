package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {
    List<UploadedDocument> findByClientId(UUID clientId);
}
