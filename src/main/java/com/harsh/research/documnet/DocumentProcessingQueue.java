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
            job.setStatus(DocumentProcessingJobStatus.COMPLETED);
            job.setLastError(null);
            job.setLockedAt(null);
            jobRepository.save(job);
        } catch (Exception exception) {
            int attempts = job.getAttempts() + 1;
            job.setAttempts(attempts);
            job.setLockedAt(null);
            job.setLastError("Document processing attempt failed");

            if (attempts >= MAX_ATTEMPTS) {
                job.setStatus(DocumentProcessingJobStatus.DEAD);
            } else {
                job.setStatus(DocumentProcessingJobStatus.PENDING);
                long delaySeconds = Math.min(300, 10L << (attempts - 1));
                job.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
            }
            jobRepository.save(job);
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
