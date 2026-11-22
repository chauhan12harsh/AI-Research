package com.harsh.research.documnet;

import com.harsh.research.ai.chunk.DocumentChunk;
import com.harsh.research.ai.chunk.DocumentChunkRepository;
import com.harsh.research.ai.embed.ChunkEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentEmbeddingStep {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkEmbeddingService chunkEmbeddingService;

    @Transactional
    public void embed(UUID documentId) {

        Document document = documentRepository
                .findByIdAndDeletedAtIsNull(documentId)
                .orElse(null);

        if (document == null) {
            return;
        }

        if (document.getStatus() != DocumentStatus.EMBEDDING) {
            return;
        }

        List<DocumentChunk> chunks =
                documentChunkRepository
                        .findByDocumentIdOrderByChunkIndex(documentId);

        if (chunks.isEmpty()) {
            document.markedFailed(
                    "NO_CHUNKS",
                    "No chunks found for document embedding"
            );
            documentRepository.save(document);
            return;
        }

        chunkEmbeddingService.embedDocument(
                document.getId(),
                document.getWorkspace().getId(),
                chunks
        );

        document.setStatus(DocumentStatus.READY);
        documentRepository.save(document);
    }
}