package com.harsh.research.documnet;

import com.harsh.research.common.ResourceNotFoundException;
import com.harsh.research.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentDeletionService {

    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DocumentProcessingJobRepository jobRepository;

    /**
     * Marks the document as deleting and releases quota exactly once.
     * The status and counters commit together. If external cleanup later fails,
     * another delete request can safely resume without decrementing counters again.
     */
    @Transactional
    public Document markDeleting(UUID documentId) throws Exception {
        Document document = documentRepository.findByIdAndDeletedAtIsNullForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (document.getStatus() != DocumentStatus.DELETING) {
            jobRepository.cancelForDocument(
                    documentId,
                    DocumentProcessingJobStatus.PENDING,
                    DocumentProcessingJobStatus.PROCESSING,
                    DocumentProcessingJobStatus.DEAD
            );
            document.setStatus(DocumentStatus.DELETING);
            documentRepository.save(document);

            if (workspaceRepository.adjustUsageCounters(
                    document.getWorkspace().getId(),
                    -1,
                    -document.getSizeBytes()
            ) != 1) {
                throw new IllegalStateException("Workspace not found while releasing document quota");
            }
        }

        return document;
    }
}
