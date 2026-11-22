package com.harsh.research.ai.retrieval;

public record RetrievedChunk(
        String documentId,
        String documentName,
        Integer pageNumber,
        Integer chunkIndex,
        Integer charStart,
        Integer charEnd,
        String content
) {}
