package com.harsh.research.ai.chunk;

import com.harsh.research.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunk extends BaseEntity {

    @Column(nullable = false,updatable = false)
    private UUID documentId;

    @Column(nullable = false,updatable = false)
    private int chunkIndex;

    @Column(nullable = false,updatable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false,updatable = false)
    private int tokenCount;

    @Column(nullable = false,updatable = false)
    private int charStart;

    @Column(nullable = false,updatable = false)
    private int charEnd;

    @Column(updatable = false)
    private Integer pageNumber;

    public static DocumentChunk form(
            UUID documentId, Chunk chunk
    ){
        DocumentChunk row = new DocumentChunk();

        row.documentId = documentId;
        row.chunkIndex = chunk.chunkIndex();
        row.content = chunk.content();
        row.tokenCount = chunk.tokenCount();
        row.charStart = chunk.charStart();
        row.charEnd = chunk.charEnd();
        row.pageNumber = chunk.pageNumber();
        return row;
    }


}




