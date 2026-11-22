package com.harsh.research.ai.retrieval;

import com.harsh.research.ai.embed.ChunkVectorMetaData;
import com.harsh.research.documnet.Document;
import com.harsh.research.documnet.DocumentRepository;
import com.harsh.research.documnet.DocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private static final int DEFAULT_TOP_K = 6;
    private static final double SIMILARITY_THRESHOLD = 0.35;

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;

    public List<RetrievedChunk> search(UUID workspaceId, String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }

        Filter.Expression workspaceFilter = new FilterExpressionBuilder()
                .eq(ChunkVectorMetaData.WORKSPACE_ID, workspaceId.toString())
                .build();

        List<org.springframework.ai.document.Document> matches =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query.trim())
                                .topK(DEFAULT_TOP_K)
                                .similarityThreshold(SIMILARITY_THRESHOLD)
                                .filterExpression(workspaceFilter)
                                .build()
                );

        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        Set<UUID> documentIds = matches.stream()
                .map(match -> parseUuid(match.getMetadata().get(
                        ChunkVectorMetaData.DOCUMENT_ID
                )))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (documentIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Document> readyDocuments = documentRepository
                .findAllByIdInAndWorkspace_IdAndStatusAndDeletedAtIsNull(
                        documentIds, workspaceId, DocumentStatus.READY
                )
                .stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<RetrievedChunk> results = new ArrayList<>();
        for (org.springframework.ai.document.Document match : matches) {
            Map<String, Object> metadata = match.getMetadata();
            UUID documentId = parseUuid(metadata.get(ChunkVectorMetaData.DOCUMENT_ID));
            Document source = documentId == null ? null : readyDocuments.get(documentId);

            // A vector is never returned unless its source document is still READY
            // and belongs to the requested workspace.
            if (source == null) {
                continue;
            }

            results.add(new RetrievedChunk(
                    source.getId().toString(),
                    source.getDisplayName(),
                    asInteger(metadata.get(ChunkVectorMetaData.PAGE_NUMBER)),
                    asInteger(metadata.get(ChunkVectorMetaData.CHUNK_INDEX)),
                    asInteger(metadata.get(ChunkVectorMetaData.CHAR_START)),
                    asInteger(metadata.get(ChunkVectorMetaData.CHAR_END)),
                    match.getText()
            ));
        }

        return List.copyOf(results);
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
