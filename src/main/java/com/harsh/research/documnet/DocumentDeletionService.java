package com.harsh.research.documnet;

import com.harsh.research.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentDeletionService {

    private final DocumentRepository documentRepository;
    private final WorkspaceService workspaceService;

    /**
     * Marks the document as deleting and releases quota exactly once.
     * The status and counters commit together. If external cleanup later fails,
     * another delete request can safely resume without decrementing counters again.
     */
    @Transactional
    public Document markDeleting(UUID documentId) throws Exception {
        Document document = documentRepository.findByIdAndDeletedAtIsNullForUpdate(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (document.getStatus() != DocumentStatus.DELETING) {
            document.setStatus(DocumentStatus.DELETING);
            documentRepository.save(document);

            workspaceService.adjustCounters(
                    document.getWorkspace().getId(),
                    -1,
                    -document.getSizeBytes()
            );
        }

        return document;
    }
}
