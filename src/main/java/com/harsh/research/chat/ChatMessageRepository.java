package com.harsh.research.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findTop12ByConversation_IdOrderByCreatedAtDesc(UUID conversationId);

    @Transactional
    void deleteAllByConversation_Id(UUID conversationId);
}
