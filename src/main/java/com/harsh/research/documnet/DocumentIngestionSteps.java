
package com.harsh.research.documnet;

import com.harsh.research.ai.chunk.Chunk;
import com.harsh.research.ai.chunk.Chunker;
import com.harsh.research.ai.chunk.DocumentChunk;
import com.harsh.research.ai.chunk.DocumentChunkRepository;
import com.harsh.research.ai.embed.ChunkEmbeddingService;
import com.harsh.research.documnet.extract.ExtractionResult;
import com.harsh.research.documnet.extract.PdfTextExtractor;
import com.harsh.research.documnet.storage.B2StorageService;
import com.harsh.research.documnet.storage.StorageKeyFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIngestionSteps {

    private final DocumentRepository documentRepository;
    private final B2StorageService b2StorageService;
    private final StorageKeyFactory storageKeyFactory;
    private final PdfTextExtractor pdfTextExtractor;
    private final LimitProperties limitProperties;
    private final Chunker chunker;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkEmbeddingService chunkEmbeddingService;

    @Transactional
    public void extract(UUID documentId) {

        Document document = getDocument(documentId);

        try {
            document.setStatus(DocumentStatus.EXTRACTING);
            documentRepository.save(document);

            ExtractionResult result;

            try (InputStream input = b2StorageService.get(document.getStorageKey())) {
                result = pdfTextExtractor.extract(input);
            }

            if (result.pageCount() > limitProperties.maxPdfPages()) {
                throw new IllegalArgumentException("PDF exceeds maximum page count");
            }

            if (result.text().isBlank()) {
                throw new IllegalArgumentException("No readable text found in PDF");
            }

            String extractedKey = storageKeyFactory.documentPrefix(
                    document.getWorkspace().getId(),
                    document.getId()
            ) + "extracted/text.txt";

            byte[] bytes = result.text().getBytes(StandardCharsets.UTF_8);

            b2StorageService.put(
                    extractedKey,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "text/plain; charset=UTF-8"
            );

            document.setExtractedKey(extractedKey);
            document.setPageCount(result.pageCount());
            document.setCharCount(result.charCount());
            document.setStatus(DocumentStatus.CHUNKING);
            documentRepository.save(document);

        } catch (Exception exception) {
            markFailed(document, "TEXT_EXTRACTION_FAILED", exception);
            throw new RuntimeException("Document extraction failed", exception);
        }
    }

    @Transactional
    public void chunk(UUID documentId) {

        Document document = getDocument(documentId);

        try {
            if (document.getExtractedKey() == null) {
                throw new IllegalStateException("Document text has not been extracted");
            }

            String text = b2StorageService.getAsString(document.getExtractedKey());
            List<Chunk> chunks = chunker.chunk(text);

            if (chunks.isEmpty()) {
                throw new IllegalStateException("No chunks were generated");
            }

            documentChunkRepository.deleteByDocumentId(documentId);

            List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(
                    chunks.stream()
                            .map(chunk -> DocumentChunk.form(documentId, chunk))
                            .toList()
            );

            document.setStatus(DocumentStatus.EMBEDDING);
            documentRepository.save(document);

            chunkEmbeddingService.embedDocument(
                    documentId,
                    document.getWorkspace().getId(),
                    savedChunks
            );

            document.setStatus(DocumentStatus.READY);
            document.setErrorCode(null);
            document.setErrorMessage(null);
            documentRepository.save(document);

        } catch (Exception exception) {
            markFailed(document, "CHUNK_OR_EMBEDDING_FAILED", exception);
            throw new RuntimeException("Document chunking or embedding failed", exception);
        }
    }

    private Document getDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    private void markFailed(Document document, String code, Exception exception) {
        document.markedFailed(
                code,
                exception.getMessage() == null
                        ? "Document processing failed"
                        : exception.getMessage()
        );
        documentRepository.save(document);
    }
}
