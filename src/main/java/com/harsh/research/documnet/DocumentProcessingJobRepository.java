package com.harsh.research.documnet;

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

    @Transactional
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
    @Transactional
    @Modifying
    @Query("""
            update DocumentProcessingJob j
            set j.status = :completed, j.lockedAt = null, j.lastError = null
            where j.id = :id and j.status = :processing
            """)
    int completeIfProcessing(
            @Param("id") UUID id,
            @Param("processing") DocumentProcessingJobStatus processing,
            @Param("completed") DocumentProcessingJobStatus completed
    );

    @Transactional
    @Modifying
    @Query("""
            update DocumentProcessingJob j
            set j.status = case when j.attempts + 1 >= :maxAttempts
                                then :dead else :pending end,
                j.attempts = j.attempts + 1,
                j.lockedAt = null,
                j.nextAttemptAt = :nextAttemptAt,
                j.lastError = :lastError
            where j.id = :id and j.status = :processing
            """)
    int failIfProcessing(
            @Param("id") UUID id,
            @Param("processing") DocumentProcessingJobStatus processing,
            @Param("pending") DocumentProcessingJobStatus pending,
            @Param("dead") DocumentProcessingJobStatus dead,
            @Param("maxAttempts") int maxAttempts,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );

    @Transactional
    @Modifying
    @Query("""
            update DocumentProcessingJob j
            set j.status = :dead, j.lockedAt = null
            where j.documentId = :documentId
              and j.status in (:pending, :processing)
            """)
    int cancelForDocument(
            @Param("documentId") UUID documentId,
            @Param("pending") DocumentProcessingJobStatus pending,
            @Param("processing") DocumentProcessingJobStatus processing,
            @Param("dead") DocumentProcessingJobStatus dead
    );

}
