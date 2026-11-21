package com.harsh.research.documnet.dto;

import com.harsh.research.documnet.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String displayName,
        String originalFileName,
        String sourceType,
        String mimeType,
        long sizeBytes,
        String status,
        Integer pageCount,
        Integer charCount,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {

    public static DocumentResponse from (Document doc){

        return new DocumentResponse(
                doc.getId(),
                doc.getDisplayName(),
                doc.getOriginalFileName(),
                doc.getSourceType().name(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                doc.getStatus().name(),
                doc.getPageCount(),
                doc.getCharCount(),
                doc.getErrorCode(),
                doc.getErrorMessage(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

}
