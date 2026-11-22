package com.harsh.research.ai.chunk;

import com.harsh.research.ai.token.TokenCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PdfChunker implements Chunker {

    private static final int TARGET_CHUNK_TOKENS = 500;
    private static final int MIN_CHUNK_SIZE_CHARS = 200;

    private final TokenCounter tokenCounter;

    private final TokenTextSplitter splitter =
            TokenTextSplitter.builder()
                    .withChunkSize(TARGET_CHUNK_TOKENS)
                    .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                    .build();

    @Override
    public List<Chunk> chunk(String extractedText) {

        List<Chunk>  chunks = new ArrayList<>();

        String[] pages = extractedText.split("\f",-1);

        int pageOffset = 0;
        int chunkIndex = 0;

        for (int p = 0; p < pages.length; p++) {

            String pageText =  pages[p];

            chunkIndex = chunkPage(
                    pageText,
                    pageOffset,
                    p+1,
                    chunkIndex,
                    chunks
            );

            pageOffset += pageText.length() + 1 ;
        }
        return chunks;
    }


    public int chunkPage(
            String pageText,
            int pageOffset,
            int pageNumber,
            int chunkIndex,
            List <Chunk> chunks
    ){

        if(pageText.isBlank()){
            return chunkIndex;
        }

        int cursor = 0;

        Document document = new Document(pageText);
        List<Document> newChunks = splitter.split(document);

        for(Document piece : newChunks){

            String content = piece.getText();

            int start = pageText.indexOf(content, cursor);

            if(start < 0){
                throw new IllegalStateException(
                        "TokenTextSplitter returned text that isn't a substring of its source page - " +
                        "can't compute a trustworthy charStart/charEnd for it"
                );
            }
            int end = start + content.length();

            Chunk chunk = new Chunk(
                    chunkIndex++,
                    content,
                    tokenCounter.count(content),
                    pageOffset+start,
                    pageOffset+end,
                    pageNumber

            );
            chunks.add(chunk);
            cursor = end;
        }
        return chunkIndex;

    }
}
