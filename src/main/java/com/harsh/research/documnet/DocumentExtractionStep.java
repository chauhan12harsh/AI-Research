package com.harsh.research.documnet;

import com.harsh.research.documnet.extract.ExtractionResult;
import com.harsh.research.documnet.extract.PdfTextExtractor;
import com.harsh.research.documnet.storage.B2StorageService;
import com.harsh.research.documnet.storage.StorageKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentExtractionStep {

    private final DocumentRepository documentRepository;
    private final B2StorageService b2StorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final StorageKeyFactory storageKeyFactory;

    public void extract(UUID documentId) {

        Document document = documentRepository
                .findByIdAndDeletedAtIsNull(documentId)
                .orElse(null);

        if (document == null ||
                document.getStatus() != DocumentStatus.UPLOADED) {
            return;
        }

        try {
            document.setStatus(DocumentStatus.EXTRACTING);
            documentRepository.save(document);

            ExtractionResult result;

            try (var inputStream = b2StorageService.get(document.getStorageKey())) {
                result = pdfTextExtractor.extract(inputStream);
            }

            if (result.text() == null || result.text().isBlank()) {
                throw new IOException("No extractable text found in PDF");
            }

            String extractedKey = storageKeyFactory.documentPrefix(
                    document.getWorkspace().getId(),
                    document.getId()
            ) + "extracted/text.txt";

            byte[] textBytes = result.text().getBytes(StandardCharsets.UTF_8);

            b2StorageService.put(
                    extractedKey,
                    new ByteArrayInputStream(textBytes),
                    textBytes.length,
                    "text/plain; charset=UTF-8"
            );

            document.setExtractedKey(extractedKey);
            document.setPageCount(result.pageCount());
            document.setCharCount(result.charCount());
            document.setStatus(DocumentStatus.CHUNKING);

            documentRepository.save(document);

        } catch (Exception e) {
            document.markedFailed(
                    "EXTRACTION_FAILED",
                    "Document text extraction failed"
            );

            documentRepository.save(document);
        }
    }
}