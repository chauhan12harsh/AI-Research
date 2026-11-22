package com.harsh.research.documnet;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentDeletionRecovery {

    private final DocumentRepository documentRepository;
    private final DocumentCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${app.deletion.reconcile-interval-ms:60000}")
    public void retryIncompleteDeletions() {
        for (Document document : documentRepository
                .findTop100ByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                        DocumentStatus.DELETING
                )) {
            try {
                cleanupService.cleanup(document);
                document.softDelete();
                documentRepository.save(document);
            } catch (RuntimeException ignored) {
                // Keep DELETING so a later scheduled pass can retry safely.
            }
        }
    }
}
