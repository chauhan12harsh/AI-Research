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
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(15);

    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentIngestionSteps ingestionSteps;
    private final DocumentRepository documentRepository;

    @Transactional
    public void enqueue(UUID documentId) {
        DocumentProcessingJob job = jobRepository.findByDocumentId(documentId)
                .orElseGet(() -> DocumentProcessingJob.create(documentId));

        job.setStatus(DocumentProcessingJobStatus.PENDING);
        job.setAttempts(0);
        job.setNextAttemptAt(Instant.now());
        job.setLockedAt(null);
        job.setLastError(null);
        jobRepository.save(job);
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
            jobRepository.releaseStale(
                    stale.getId(),
                    DocumentProcessingJobStatus.PROCESSING,
                    DocumentProcessingJobStatus.PENDING,
                    now,
                    staleBefore
            );
        }
    }
}
