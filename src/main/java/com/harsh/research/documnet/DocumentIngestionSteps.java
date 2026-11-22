package com.harsh.research.documnet;

import com.harsh.research.ai.chunk.Chunk;
import com.harsh.research.ai.chunk.Chunker;
import com.harsh.research.ai.chunk.DocumentChunk;
import com.harsh.research.ai.chunk.DocumentChunkRepository;
import com.harsh.research.documnet.storage.B2StorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIngestionSteps {

    private final DocumentRepository  documentRepository;
    private final B2StorageService b2StorageService;
    private final Chunker chunker;
    private final DocumentChunkRepository documentChunkRepository;

    @Transactional
    public void chunk(UUID documentId) {

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);

        if(document == null) {
            return;
        }

        String text = b2StorageService.getAsString(document.getExtractedKey());

        List<Chunk> chunks = chunker.chunk(text);

        documentChunkRepository.deleteByDocumentId(documentId);

        documentChunkRepository.saveAll(
                chunks.stream()
                        .map(chunk -> DocumentChunk.form(document.getId(), chunk)).toList()
        );

        document.setStatus(DocumentStatus.EMBEDDING);
        documentRepository.save(document);
    }

}
