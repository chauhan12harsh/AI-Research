package com.harsh.research.ai.retrieval.dto;

import com.harsh.research.ai.retrieval.RetrievedChunk;

import java.util.List;
import java.util.UUID;

public record AskResponse(
        UUID conversationId,
        String answer,
        List<RetrievedChunk> citations
) {
    public AskResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
