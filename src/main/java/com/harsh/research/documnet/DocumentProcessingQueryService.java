package com.harsh.research.documnet;

import com.harsh.research.documnet.dto.DocumentProcessingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentProcessingQueryService {

    private final DocumentService documentService;
    private final DocumentProcessingJobRepository jobRepository;

    public DocumentProcessingResponse get(UUID documentId, UUID ownerId) throws Exception {
        documentService.get(documentId, ownerId);

        return jobRepository.findByDocumentId(documentId)
                .map(job -> new DocumentProcessingResponse(
                        job.getStatus().name(),
                        job.getAttempts(),
                        job.getNextAttemptAt(),
                        job.getLastError()
                ))
                .orElseGet(() -> new DocumentProcessingResponse(
                        "NOT_QUEUED", 0, null, null
                ));
    }
}
