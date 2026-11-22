package com.harsh.research.ai.chunk;

import com.harsh.research.ai.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfChunkerTest {

    private final PdfChunker chunker = new PdfChunker(new TokenCounter());

    @Test
    void createsOrderedChunksWithValidSourceOffsetsAcrossPages() {
        String pageOne = String.join(" ", Collections.nCopies(900, "research"));
        String pageTwo = String.join(" ", Collections.nCopies(700, "evidence"));
        String source = pageOne + "\f" + pageTwo;

        List<Chunk> chunks = chunker.chunk(source);

        assertTrue(chunks.size() > 2);
        assertEquals(
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toList(),
                chunks.stream().map(Chunk::chunkIndex).toList()
        );

        for (Chunk chunk : chunks) {
            assertTrue(chunk.charStart() >= 0);
            assertTrue(chunk.charEnd() > chunk.charStart());
            assertTrue(chunk.charEnd() <= source.length());
            assertEquals(
                    chunk.content(),
                    source.substring(chunk.charStart(), chunk.charEnd())
            );
        }

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.pageNumber() == 1));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.pageNumber() == 2));
    }
}
