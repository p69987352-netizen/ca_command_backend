package com.caCommand.caCommand.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "uploaded_documents", indexes = {
        @Index(name = "idx_doc_client", columnList = "client_id")
})
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class UploadedDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 100)
    private String documentType; // e.g. AIS, FORM_16, NOTICE

    // E.g. local path or S3 URL
    @Column(length = 500)
    private String storagePath;

    @CreationTimestamp
    private LocalDateTime uploadDate;

    // Future-Proof AI Layer
    @Column(name = "raw_document", columnDefinition = "TEXT")
    private String rawDocument;

    @Column(name = "extracted_json", columnDefinition = "TEXT")
    private String extractedJson;

    @Column(name = "analysis_json", columnDefinition = "TEXT")
    private String analysisJson;
}
