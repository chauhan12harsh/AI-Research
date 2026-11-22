
package com.harsh.research.documnet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentProcessingStatusService {

    private final DocumentRepository documentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID documentId, DocumentStatus status) {

        Document document = findDocument(documentId);
        if (document.getStatus() == DocumentStatus.DELETING) {
            throw new IllegalStateException("Document is being deleted");
        }

        document.setStatus(status);
        documentRepository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(UUID documentId) {

        Document document = findDocument(documentId);
        if (document.getStatus() == DocumentStatus.DELETING) {
            throw new IllegalStateException("Document is being deleted");
        }

        document.setStatus(DocumentStatus.READY);
        document.setErrorCode(null);
        document.setErrorMessage(null);

        documentRepository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            UUID documentId,
            String errorCode,
            String errorMessage
    ) {

        Document document = findDocument(documentId);
        if (document.getStatus() == DocumentStatus.DELETING) {
            return;
        }

        document.markedFailed(
                errorCode,
                errorMessage == null
                        ? "Document processing failed"
                        : errorMessage
        );

        documentRepository.save(document);
    }

    private Document findDocument(UUID documentId) {

        return documentRepository
                .findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found")
                );
    }
}
