package com.harsh.research.ai.chunk;

import java.util.List;

public interface Chunker {

    List<Chunk> chunk(String extractedText);
}
