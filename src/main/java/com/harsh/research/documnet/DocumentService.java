
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
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final WorkspaceService workspaceService;
    private final LimitProperties limitProperties;
    private final DocumentValidator documentValidator;
    private final DocumentRepository documentRepository;
    private final StorageProperties storageProperties;
    private final StorageKeyFactory storageKeyFactory;
    private final B2StorageService b2StorageService;
    private final DocumentIngestionSteps ingestionSteps;

    public Document upload(
            UUID workspaceId,
            UUID ownerId,
            MultipartFile file,
            String displayNameOverride
    ) throws Exception {

        Workspace workspace = workspaceService.get(workspaceId, ownerId);

        DocumentValidator.ValidateUpload validated = documentValidator.validate(file);

        assertWithinQuota(workspace, validated.sizeBytes());

        Document exists = documentRepository
                .findByWorkspace_IdAndChecksumSha256AndDeletedAtIsNull(
                        workspaceId, validated.checksumSha256()
                ).orElse(null);

        if (exists != null) {
            return exists;
        }

        String displayName = displayNameOverride != null && !displayNameOverride.isBlank()
                ? displayNameOverride
                : file.getOriginalFilename();

        Document doc = Document.create(
                workspace,
                ownerId,
                displayName,
                file.getOriginalFilename(),
                validated.sourceType(),
                validated.mimeType(),
                validated.sizeBytes(),
                validated.checksumSha256(),
                storageProperties.bucket(),
                "pending"
        );

        doc = documentRepository.save(doc);

        String storageKey = storageKeyFactory.originalKey(
                workspaceId,
                doc.getId(),
                file.getOriginalFilename()
        );

        doc.setStorageKey(storageKey);

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : validated.mimeType();

        try {
            b2StorageService.put(
                    storageKey,
                    file.getInputStream(),
                    file.getSize(),
                    contentType
            );
        } catch (IOException e) {
            doc.markedFailed(
                    "STORAGE_WRITE_FAILED",
                    "Could not upload the file - please try again"
            );
            documentRepository.save(doc);
            throw new UncheckedIOException(e);
        }

        doc.setStatus(DocumentStatus.UPLOADED);
        documentRepository.save(doc);

        workspaceService.adjustCounters(workspaceId, 1, validated.sizeBytes());

        ingestionSteps.extract(doc.getId());
        ingestionSteps.chunk(doc.getId());

        return documentRepository.findByIdAndDeletedAtIsNull(doc.getId())
                .orElseThrow(() -> new IllegalStateException("Document not found"));
    }

    public Document get(UUID documentId, UUID ownerId) throws Exception {
        return documentRepository.findByIdAndWorkspace_OwnerIdAndDeletedAtIsNull(
                documentId,
                ownerId
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

        String normalizedQ = q == null ? "" : q;

        return documentRepository.search(
                workspaceId,
                status,
                sourceType,
                normalizedQ,
                pageable
        );
    }

    public PresignedUrl presignDownload(
            UUID documentId,
            UUID userId
    ) throws Exception {

        Document doc = get(documentId, userId);

        return b2StorageService.presignedGet(doc.getStorageKey(), DOWNLOAD_TTL);
    }

    public DocumentContentResponse getContent(
            UUID documentId,
            UUID ownerId,
            int offset,
            int limit
    ) throws Exception {

        Document doc = get(documentId, ownerId);

        if (doc.getStatus() != DocumentStatus.READY || doc.getExtractedKey() == null) {
            throw new Exception("This document hasn't finished processing");
        }

        String full = b2StorageService.getAsString(doc.getExtractedKey());

        int from = Math.min(Math.max(offset, 0), full.length());
        int to = Math.min(from + Math.max(limit, 0), full.length());

        return new DocumentContentResponse(
                full.substring(from, to),
                from,
                to - from,
                full.length()
        );
    }

    public Document retry(
            UUID documentId,
            UUID ownerId
    ) throws Exception {

        Document doc = get(documentId, ownerId);

        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new Exception("Only a failed document can be retried");
        }

        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setErrorCode(null);
        doc.setErrorMessage(null);
        documentRepository.save(doc);

        ingestionSteps.extract(doc.getId());
        ingestionSteps.chunk(doc.getId());

        return documentRepository.findByIdAndDeletedAtIsNull(doc.getId())
                .orElseThrow(() -> new IllegalStateException("Document not found"));
    }

    public void delete(
            UUID documentId,
            UUID ownerId
    ) throws Exception {

        Document doc = get(documentId, ownerId);

        doc.softDelete();
        documentRepository.save(doc);

        workspaceService.adjustCounters(
                doc.getWorkspace().getId(),
                -1,
                -doc.getSizeBytes()
        );
    }

    private void assertWithinQuota(
            Workspace workspace,
            long additionalBytes
    ) throws Exception {

        long limit = limitProperties.workspaceBytes();

        if (workspace.getByteCount() + additionalBytes > limit) {
            throw new Exception("This workspace limit is " + limit + " bytes");
        }
    }
}
