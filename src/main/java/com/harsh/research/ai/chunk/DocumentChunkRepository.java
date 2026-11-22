
package com.harsh.research.ai.chunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository
        extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(
            UUID documentId
    );

    long countByDocumentId(UUID documentId);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM DocumentChunk c
        WHERE c.documentId = :documentId
    """)
    void deleteByDocumentId(
            @Param("documentId") UUID documentId
    );
}
