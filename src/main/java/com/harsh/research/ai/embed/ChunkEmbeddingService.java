package com.harsh.research.ai.embed;

import com.harsh.research.ai.chunk.DocumentChunk;
import com.harsh.research.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChunkEmbeddingService {

    private final VectorStore vectorStore;
    private final EmbeddingProperties embeddingProperties;

    public void embedDocument(
            UUID documentId,
            UUID workspaceId,
            List<DocumentChunk> chunks
    ){

        deleteDocument(documentId);

        if(chunks.isEmpty()){
            return;
        }

        int batchSize = Math.max(embeddingProperties.batchSize(),1);

        for (int from = 0; from < chunks.size(); from += batchSize) {

            int to = Math.min(from + batchSize,chunks.size());

            List<Document> batch = toDocuments(documentId,workspaceId,chunks.subList(from, to));

            vectorStore.add(batch);

        }



    }


    public void deleteDocument(UUID  documentId){

        Filter.Expression byDocument = new FilterExpressionBuilder()
                .eq(ChunkVectorMetaData.DOCUMENT_ID,documentId.toString())
                .build();

        vectorStore.delete(byDocument);

    }

    public List<Document> toDocuments(
            UUID documentId,
            UUID workspaceId,
            List<DocumentChunk> chunks
    ){

        List<Document> documents = new ArrayList<>(chunks.size());

        for(DocumentChunk chunk: chunks){

            Map<String,Object> metaData = new HashMap<>();

            metaData.put(ChunkVectorMetaData.DOCUMENT_ID,documentId.toString());
            metaData.put(ChunkVectorMetaData.WORKSPACE_ID,documentId.toString());
            metaData.put(ChunkVectorMetaData.CHUNK_INDEX,documentId.toString());
            metaData.put(ChunkVectorMetaData.CHAR_START,documentId.toString());
            metaData.put(ChunkVectorMetaData.CHAR_END,documentId.toString());

            if(chunk.getPageNumber() != null){
                metaData.put(ChunkVectorMetaData.PAGE_NUMBER,chunk.getPageNumber());
            }

            Document document = Document.builder()
                    .id(ChunkVectorMetaData.vectorId(
                            documentId,chunk.getChunkIndex()
                    ))
                    .text(chunk.getContent())
                    .metadata(metaData)
                    .build();

            documents.add(document);
        }

        return documents;
    }
}
