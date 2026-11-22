package com.harsh.research.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findAllByWorkspace_IdAndDeletedAtIsNull(UUID workspaceId);

    Optional<Conversation> findByIdAndWorkspace_IdAndUserIdAndDeletedAtIsNull(
            UUID id,
            UUID workspaceId,
            UUID userId
    );

    Page<Conversation> findAllByWorkspace_IdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID workspaceId,
            UUID userId,
            Pageable pageable
    );
}
