
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
    private final DocumentProcessingStatusService statusService;

    public void process(UUID documentId) {

        try {
            extract(documentId);
            chunkAndEmbed(documentId);

        } catch (Exception exception) {

            statusService.markFailed(
                    documentId,
                    "DOCUMENT_PROCESSING_FAILED",
                    safeMessage(exception)
            );

            throw new IllegalStateException(
                    "Document processing failed",
                    exception
            );
        }
    }

    private void extract(UUID documentId) throws Exception {

        Document document = getDocument(documentId);

        statusService.updateStatus(
                documentId,
                DocumentStatus.EXTRACTING
        );

        ExtractionResult result;

        try (InputStream input =
                     b2StorageService.get(document.getStorageKey())) {

            result = pdfTextExtractor.extract(input);
        }

        if (result.pageCount() > limitProperties.maxPdfPages()) {
            throw new IllegalArgumentException(
                    "PDF exceeds maximum page count"
            );
        }

        if (result.text() == null || result.text().isBlank()) {
            throw new IllegalArgumentException(
                    "No readable text found in PDF"
            );
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

        updateExtractionMetadata(
                documentId,
                extractedKey,
                result.pageCount(),
                result.charCount()
        );
    }

    private void chunkAndEmbed(UUID documentId) throws Exception {

        Document document = getDocument(documentId);

        if (document.getExtractedKey() == null) {
            throw new IllegalStateException(
                    "Extracted document text is missing"
            );
        }

        statusService.updateStatus(
                documentId,
                DocumentStatus.CHUNKING
        );

        String text = b2StorageService.getAsString(
                document.getExtractedKey()
        );

        List<Chunk> chunks = chunker.chunk(text);

        if (chunks.isEmpty()) {
            throw new IllegalStateException(
                    "No chunks generated"
            );
        }

        documentChunkRepository.deleteByDocumentId(documentId);

        List<DocumentChunk> savedChunks =
                documentChunkRepository.saveAll(
                        chunks.stream()
                                .map(chunk ->
                                        DocumentChunk.form(documentId, chunk)
                                )
                                .toList()
                );

        statusService.updateStatus(
                documentId,
                DocumentStatus.EMBEDDING
        );

        chunkEmbeddingService.embedDocument(
                documentId,
                document.getWorkspace().getId(),
                savedChunks
        );

        statusService.markReady(documentId);
    }

    private void updateExtractionMetadata(
            UUID documentId,
            String extractedKey,
            int pageCount,
            int charCount
    ) {

        Document document = getDocument(documentId);

        document.setExtractedKey(extractedKey);
        document.setPageCount(pageCount);
        document.setCharCount(charCount);
        document.setStatus(DocumentStatus.CHUNKING);

        documentRepository.save(document);
    }

    private Document getDocument(UUID documentId) {

        return documentRepository
                .findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found")
                );
    }

    private String safeMessage(Exception exception) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "An error occurred while processing the document";
        }

        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }
}
