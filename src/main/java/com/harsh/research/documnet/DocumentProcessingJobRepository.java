package com.harsh.research.documnet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentProcessingJobRepository
        extends JpaRepository<DocumentProcessingJob, UUID> {

    Optional<DocumentProcessingJob> findByDocumentId(UUID documentId);

    List<DocumentProcessingJob> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            DocumentProcessingJobStatus status,
            Instant now
    );

    List<DocumentProcessingJob> findTop20ByStatusAndLockedAtBeforeOrderByLockedAtAsc(
            DocumentProcessingJobStatus status,
            Instant staleBefore
    );

    @Transactional
    @Transactional
    @Modifying
    @Query("""
            update DocumentProcessingJob j
            set j.status = :processing, j.lockedAt = :now
            where j.id = :id
              and j.status = :pending
              and j.nextAttemptAt <= :now
            """)
    int claim(
            @Param("id") UUID id,
            @Param("pending") DocumentProcessingJobStatus pending,
            @Param("processing") DocumentProcessingJobStatus processing,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            update DocumentProcessingJob j
            set j.status = :pending, j.lockedAt = null, j.nextAttemptAt = :now
            where j.id = :id
              and j.status = :processing
              and j.lockedAt < :staleBefore
            """)
    int releaseStale(
            @Param("id") UUID id,
            @Param("processing") DocumentProcessingJobStatus processing,
            @Param("pending") DocumentProcessingJobStatus pending,
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore
    );
}
