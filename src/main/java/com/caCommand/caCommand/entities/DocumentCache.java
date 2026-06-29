package com.caCommand.caCommand.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@lombok.EqualsAndHashCode(callSuper=false)
public class DocumentCache extends BaseEntity {

    @Id
    @Column(length = 64)
    private String documentHash;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @Column(columnDefinition = "TEXT")
    private String structuredJson;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    private LocalDateTime processedAt;
}
