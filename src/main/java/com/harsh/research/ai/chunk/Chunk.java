package com.harsh.research.ai.chunk;

public record Chunk(
        int chunkIndex,
        String content,
        int tokenCount,
        int charStart,
        int charEnd,
        Integer pageNumber
) {
}
