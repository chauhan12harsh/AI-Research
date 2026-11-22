package com.harsh.research.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findByIdAndOwnerIdAndDeletedAtNull(UUID id, UUID ownerId);

    Optional<Workspace> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Transactional
    @Modifying
    @Query("""
        UPDATE Workspace w
        SET w.docCount = w.docCount + 1,
            w.byteCount = w.byteCount + :bytes
        WHERE w.id = :workspaceId
          AND w.deletedAt IS NULL
          AND :bytes > 0
          AND w.byteCount <= :maxCurrentBytes
    """)
    int reserveDocumentStorage(
            @Param("workspaceId") UUID workspaceId,
            @Param("bytes") long bytes,
            @Param("maxCurrentBytes") long maxCurrentBytes
    );

    @Transactional
    @Modifying
    @Query("""
        UPDATE Workspace w
        SET w.docCount = CASE
                WHEN w.docCount + :docDelta < 0 THEN 0
                ELSE w.docCount + :docDelta
            END,
            w.byteCount = CASE
                WHEN w.byteCount + :bytesDelta < 0 THEN 0
                ELSE w.byteCount + :bytesDelta
            END
        WHERE w.id = :workspaceId
    """)
    int adjustUsageCounters(
            @Param("workspaceId") UUID workspaceId,
            @Param("docDelta") int docDelta,
            @Param("bytesDelta") long bytesDelta
    );

    @Transactional
    @Modifying
    @Query("""
        UPDATE Workspace w
        SET w.chatCount = CASE
                WHEN w.chatCount + :delta < 0 THEN 0
                ELSE w.chatCount + :delta
            END
        WHERE w.id = :workspaceId
    """)
    int adjustChatCounter(
            @Param("workspaceId") UUID workspaceId,
            @Param("delta") int delta
    );

    List<Workspace> findAllByOwnerIdAndDeletedAtNullOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerIdAndDeletedAtNullOrderByCreatedAtDesc(UUID ownerId);

    @Query("""
        select (count(w) > 0) from Workspace w
        where w.ownerId = :ownerId
          and lower(w.name) = lower(:name)
          and w.deletedAt is null
    """)
    boolean existsByOwnerAndName(
            @Param("ownerId") UUID ownerId,
            @Param("name") String name
    );
}
