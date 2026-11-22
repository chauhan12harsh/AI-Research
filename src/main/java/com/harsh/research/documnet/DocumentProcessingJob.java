package com.harsh.research.documnet;

import com.harsh.research.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "document_processing_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_processing_job_document",
                columnNames = "document_id"
        ),
        indexes = @Index(
                name = "idx_document_processing_job_due",
                columnList = "status, next_attempt_at"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class DocumentProcessingJob extends BaseEntity {

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentProcessingJobStatus status = DocumentProcessingJobStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(length = 500)
    private String lastError;

    public static DocumentProcessingJob create(UUID documentId) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.documentId = documentId;
        job.nextAttemptAt = Instant.now();
        return job;
    }
}
