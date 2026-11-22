package com.harsh.research.documnet;

import com.harsh.research.config.StorageProperties;
import com.harsh.research.documnet.dto.DocumentContentResponse;
import com.harsh.research.documnet.storage.B2StorageService;
import com.harsh.research.documnet.storage.PresignedUrl;
import com.harsh.research.documnet.storage.StorageKeyFactory;
import com.harsh.research.workspace.Workspace;
import com.harsh.research.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);
    private static final String PENDING_STORAGE_KEY = "pending";
    private static final int MAX_CONTENT_RESPONSE_CHARS = 50_000;

    private final WorkspaceService workspaceService;
    private final LimitProperties limitProperties;
    private final DocumentValidator documentValidator;
    private final DocumentRepository documentRepository;
    private final StorageProperties storageProperties;
    private final StorageKeyFactory storageKeyFactory;
    private final B2StorageService b2StorageService;
    private final DocumentIngestionSteps ingestionSteps;
    private final DocumentCleanupService cleanupService;
    private final DocumentDeletionService deletionService;

    public Document upload(
            UUID workspaceId,
            UUID ownerId,
            MultipartFile file,
            String displayNameOverride
    ) throws Exception {

        Workspace workspace = workspaceService.get(workspaceId, ownerId);
        DocumentValidator.ValidateUpload validated = documentValidator.validate(file);

        Document duplicate = documentRepository
                .findByWorkspace_IdAndChecksumSha256AndDeletedAtIsNull(
                        workspaceId, validated.checksumSha256()
                ).orElse(null);
        if (duplicate != null) {
            boolean failedUploadWithoutSource =
                    duplicate.getStatus() == DocumentStatus.FAILED
                            && (isMissingStorageKey(duplicate.getStorageKey())
                                || !b2StorageService.exists(duplicate.getStorageKey()));

            if (!failedUploadWithoutSource) {
                return duplicate;
            }

            // A failed storage write leaves no source object to retry. Retire
            // that record so the same file can be uploaded again.
            duplicate.softDelete();
            documentRepository.save(duplicate);
        }

        String originalName = file.getOriginalFilename();
        String displayName = displayNameOverride != null && !displayNameOverride.isBlank()
                ? displayNameOverride.trim()
                : (originalName == null || originalName.isBlank()
                    ? "Untitled document.pdf" : originalName);

        Document document = Document.create(
                workspace, ownerId, displayName, originalName,
                validated.sourceType(), validated.mimeType(), validated.sizeBytes(),
                validated.checksumSha256(), storageProperties.bucket(), PENDING_STORAGE_KEY
        );
        document = documentRepository.save(document);

        String storageKey = storageKeyFactory.originalKey(
                workspaceId, document.getId(), originalName
        );
        document.setStorageKey(storageKey);
        documentRepository.save(document);

        // Create the database row first; reserve capacity only after that succeeds.
        if (!workspaceService.reserveDocumentStorage(
                workspaceId,
                validated.sizeBytes(),
                limitProperties.workspaceBytes()
        )) {
            document.softDelete();
            documentRepository.save(document);
            throw new Exception(
                    "Workspace storage limit exceeded. Maximum allowed: "
                            + limitProperties.workspaceBytes() + " bytes"
            );
        }

        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? validated.mimeType() : file.getContentType();

        try (InputStream input = file.getInputStream()) {
            b2StorageService.put(storageKey, input, validated.sizeBytes(), contentType);
        } catch (IOException | RuntimeException exception) {
            markUploadFailed(document, "STORAGE_WRITE_FAILED");
            try {
                b2StorageService.delete(storageKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            try {
                workspaceService.adjustCounters(
                        workspaceId, -1, -validated.sizeBytes()
                );
            } catch (Exception counterFailure) {
                exception.addSuppressed(counterFailure);
            }
            if (exception instanceof IOException ioException) {
                throw new UncheckedIOException("Unable to read uploaded file", ioException);
            }
            throw (RuntimeException) exception;
        }

        try {
            document.setStatus(DocumentStatus.UPLOADED);
            documentRepository.save(document);
        } catch (RuntimeException persistenceFailure) {
            // Keep the original object and reserved quota: if the status write
            // failed, retaining the file allows an operator or retry flow to
            // recover the document rather than creating an orphan.
            try {
                markUploadFailed(document, "DOCUMENT_STATUS_UPDATE_FAILED");
            } catch (RuntimeException statusFailure) {
                persistenceFailure.addSuppressed(statusFailure);
            }
            throw persistenceFailure;
        }

        // Processing failures are persisted as FAILED by DocumentIngestionSteps.
        // Return the document so the client can display its state and offer retry.
        try {
            ingestionSteps.process(document.getId());
        } catch (RuntimeException ignored) {
            // Status and error details are recorded by the ingestion pipeline.
        }

        return documentRepository.findByIdAndDeletedAtIsNull(document.getId())
                .orElseThrow(() -> new IllegalStateException("Document not found after upload"));
    }

    public Document get(UUID documentId, UUID ownerId) throws Exception {
        return documentRepository.findByIdAndWorkspace_OwnerIdAndDeletedAtIsNull(
                documentId, ownerId
        ).orElseThrow(() -> new Exception("Document not found"));
    }

    public Page<Document> search(
            UUID workspaceId,
            UUID ownerId,
            DocumentStatus status,
            SourceType sourceType,
            String q,
            Pageable pageable
    ) throws Exception {
        workspaceService.get(workspaceId, ownerId);
        return documentRepository.search(
                workspaceId, status, sourceType, q == null ? "" : q.trim(), pageable
        );
    }

    public PresignedUrl presignDownload(UUID documentId, UUID ownerId) throws Exception {
        Document document = get(documentId, ownerId);
        if (document.getStatus() == DocumentStatus.DELETING
                || isMissingStorageKey(document.getStorageKey())) {
            throw new Exception("Original document file is not available");
        }
        if (!b2StorageService.exists(document.getStorageKey())) {
            throw new Exception("Original document file is missing from storage");
        }
        return b2StorageService.presignedGet(document.getStorageKey(), DOWNLOAD_TTL);
    }

    public DocumentContentResponse getContent(
            UUID documentId, UUID ownerId, int offset, int limit
    ) throws Exception {
        Document document = get(documentId, ownerId);
        if (document.getStatus() != DocumentStatus.READY
                || document.getExtractedKey() == null
                || document.getExtractedKey().isBlank()) {
            throw new Exception("This document has not finished processing");
        }

        String fullText = b2StorageService.getAsString(document.getExtractedKey());
        int from = Math.max(0, Math.min(offset, fullText.length()));
        int boundedLimit = Math.min(Math.max(0, limit), MAX_CONTENT_RESPONSE_CHARS);
        int to = (int) Math.min((long) from + boundedLimit, fullText.length());
        String content = fullText.substring(from, to);

        return new DocumentContentResponse(content, from, content.length(), fullText.length());
    }

    public Document retry(UUID documentId, UUID ownerId) throws Exception {
        Document document = get(documentId, ownerId);
        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new Exception("Only a failed document can be retried");
        }
        if (isMissingStorageKey(document.getStorageKey())
                || !b2StorageService.exists(document.getStorageKey())) {
            throw new Exception("Original document file is unavailable; upload it again");
        }

        document.setStatus(DocumentStatus.UPLOADED);
        document.setErrorCode(null);
        document.setErrorMessage(null);
        documentRepository.save(document);

        try {
            ingestionSteps.process(document.getId());
        } catch (RuntimeException ignored) {
            // The ingestion pipeline records FAILED status and a safe error message.
        }

        return documentRepository.findByIdAndDeletedAtIsNull(document.getId())
                .orElseThrow(() -> new IllegalStateException("Document not found after retry"));
    }

    public void delete(UUID documentId, UUID ownerId) throws Exception {
        get(documentId, ownerId);

        // Status + quota release are committed together and happen only once.
        // If external cleanup fails, a repeated DELETE resumes this operation.
        Document document = deletionService.markDeleting(documentId);
        cleanupService.cleanup(document);

        document.softDelete();
        documentRepository.save(document);
    }

    private void markUploadFailed(Document document, String code) {
        document.markedFailed(code, "Document upload could not be completed");
        documentRepository.save(document);
    }

    private boolean isMissingStorageKey(String key) {
        return key == null || key.isBlank() || PENDING_STORAGE_KEY.equals(key);
    }
}
