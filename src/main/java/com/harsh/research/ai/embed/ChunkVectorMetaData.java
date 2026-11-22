package com.harsh.research.ai.embed;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ChunkVectorMetaData {

    public static final String DOCUMENT_ID="documentId";
    public static final String WORKSPACE_ID = "workspaceId";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String PAGE_NUMBER = "pageNumber";
    public static final String CHAR_START = "charStart";
    public static final String CHAR_END = "charEnd";

    private ChunkVectorMetaData(){

    }

    public static String vectorId(UUID  documentId, int chunkIndex){

        return UUID.nameUUIDFromBytes(
                (documentId + ":" +chunkIndex).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

}


