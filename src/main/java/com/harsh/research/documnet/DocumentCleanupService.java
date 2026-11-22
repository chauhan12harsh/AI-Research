
package com.harsh.research.documnet;

import com.harsh.research.ai.chunk.DocumentChunkRepository;
import com.harsh.research.ai.embed.ChunkEmbeddingService;
import com.harsh.research.documnet.storage.B2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentCleanupService {

    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkEmbeddingService chunkEmbeddingService;
    private final B2StorageService b2StorageService;

    public void cleanup(Document document) {

        // Remove vectors from PGVector
        chunkEmbeddingService.deleteDocument(document.getId());

        // Remove stored chunks from PostgreSQL
        documentChunkRepository.deleteByDocumentId(document.getId());

        // Remove extracted text from B2
        if (document.getExtractedKey() != null) {
            b2StorageService.delete(document.getExtractedKey());
        }

        // Remove original file from B2
        if (document.getStorageKey() != null) {
            b2StorageService.delete(document.getStorageKey());
        }
    }
}
