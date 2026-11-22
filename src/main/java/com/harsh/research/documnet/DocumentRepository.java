package com.harsh.research.documnet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndWorkspace_OwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Document> findByWorkspace_IdAndChecksumSha256AndDeletedAtIsNull(UUID workspaceId, String checksum);

    Optional<Document> findByIdAndWorkspace_IdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    @Query("""
        Select d from Document d
        Where d.workspace.id = :workspaceId and d.deletedAt is null
        and (:status is null or d.status = :status)
        and (:sourceType is null or d.sourceType = :sourceType)
        and lower(d.displayName) like lower(CONCAT('%', :q , '%'))
    """)
    Page<Document> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") DocumentStatus status,
            @Param("sourceType") SourceType sourceType,
            @Param("q") String q,
            Pageable pageable

    );


}
