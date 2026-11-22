package com.harsh.research.documnet;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentProcessingQueue {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TIMEOUT = Duration.ofHours(2);

    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentIngestionSteps ingestionSteps;
    private final DocumentRepository documentRepository;
    private final DocumentProcessingStatusService statusService;

    @Transactional
    public void enqueue(UUID documentId) {
        Instant now = Instant.now();
        DocumentProcessingJob existing = jobRepository.findByDocumentId(documentId)
                .orElse(null);

        if (existing == null) {
            jobRepository.insertIfMissing(UUID.randomUUID(), documentId, now);
            return;
        }

        existing.setStatus(DocumentProcessingJobStatus.PENDING);
        existing.setAttempts(0);
        existing.setNextAttemptAt(now);
        existing.setLockedAt(null);
        existing.setLastError(null);
        jobRepository.save(existing);
    }

    @Scheduled(fixedDelayString = "${app.ingestion.poll-interval-ms:5000}")
    public void poll() {
        recoverStaleJobs();

        Instant now = Instant.now();
        for (DocumentProcessingJob candidate :
                jobRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        DocumentProcessingJobStatus.PENDING, now
                )) {
            if (jobRepository.claim(
                    candidate.getId(),
                    DocumentProcessingJobStatus.PENDING,
                    DocumentProcessingJobStatus.PROCESSING,
                    now
            ) != 1) {
                continue;
            }
            processClaimed(candidate.getId());
        }
    }

    private void processClaimed(UUID jobId) {
        DocumentProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        try {
            ingestionSteps.process(job.getDocumentId());
            jobRepository.completeIfProcessing(
                    jobId,
                    DocumentProcessingJobStatus.PROCESSING,
                    DocumentProcessingJobStatus.COMPLETED
            );
        } catch (Exception exception) {
            int nextAttempt = job.getAttempts() + 1;
            long delaySeconds = Math.min(300, 10L << Math.min(nextAttempt - 1, 5));
            jobRepository.failIfProcessing(
                    jobId,
                    DocumentProcessingJobStatus.PROCESSING,
                    DocumentProcessingJobStatus.PENDING,
                    DocumentProcessingJobStatus.DEAD,
                    MAX_ATTEMPTS,
                    Instant.now().plusSeconds(delaySeconds),
                    "Document processing attempt failed"
            );
        }
    }

    @Scheduled(fixedDelayString = "${app.ingestion.reconcile-interval-ms:60000}")
    public void reconcileUnqueuedDocuments() {
        for (Document document : documentRepository
                .findTop100ByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                        DocumentStatus.UPLOADED
                )) {
            if (jobRepository.findByDocumentId(document.getId()).isEmpty()) {
                enqueue(document.getId());
            }
        }
    }

    private void recoverStaleJobs() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(LOCK_TIMEOUT);

        for (DocumentProcessingJob stale :
                jobRepository.findTop20ByStatusAndLockedAtBeforeOrderByLockedAtAsc(
                        DocumentProcessingJobStatus.PROCESSING, staleBefore
                )) {
            int released = jobRepository.releaseStale(
                    stale.getId(),
                    DocumentProcessingJobStatus.PROCESSING,
                    DocumentProcessingJobStatus.PENDING,
                    DocumentProcessingJobStatus.DEAD,
                    MAX_ATTEMPTS,
                    now,
                    staleBefore
            );
            if (released == 1) {
                DocumentProcessingJob refreshed = jobRepository.findById(stale.getId())
                        .orElse(null);
                if (refreshed != null
                        && refreshed.getStatus() == DocumentProcessingJobStatus.DEAD) {
                    try {
                        statusService.markFailed(
                                refreshed.getDocumentId(),
                                "PROCESSING_WORKER_TIMEOUT",
                                "Document processing repeatedly timed out"
                        );
                    } catch (RuntimeException ignored) {
                        // The document may already be deleting; cleanup owns that state.
                    }
                }
            }
        }
    }
}
