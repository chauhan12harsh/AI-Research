package com.harsh.research.chat;

import com.harsh.research.chat.dto.ChatMessageResponse;
import com.harsh.research.chat.dto.ConversationResponse;
import com.harsh.research.workspace.Workspace;
import com.harsh.research.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceService workspaceService;

    @Transactional
    public Conversation getOrCreate(
            UUID workspaceId,
            UUID userId,
            UUID conversationId,
            String initialQuestion
    ) throws Exception {
        if (conversationId != null) {
            return conversationRepository
                    .findByIdAndWorkspace_IdAndUserIdAndDeletedAtIsNull(
                            conversationId, workspaceId, userId
                    )
                    .orElseThrow(() ->
                            new IllegalArgumentException("Conversation not found")
                    );
        }

        Workspace workspace = workspaceService.get(workspaceId, userId);
        String title = initialQuestion == null ? "New conversation"
                : initialQuestion.trim();
        if (title.isBlank()) {
            title = "New conversation";
        }
        if (title.length() > 120) {
            title = title.substring(0, 117) + "...";
        }

        Conversation conversation = conversationRepository.save(
                Conversation.create(workspace, userId, title)
        );
        workspaceService.adjustChatCount(workspaceId, 1);
        return conversation;
    }

    public Page<ConversationResponse> list(
            UUID workspaceId,
            UUID userId,
            Pageable pageable
    ) throws Exception {
        workspaceService.get(workspaceId, userId);
        return conversationRepository
                .findAllByWorkspace_IdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        workspaceId, userId, pageable
                )
                .map(conversation -> new ConversationResponse(
                        conversation.getId(),
                        conversation.getTitle(),
                        conversation.getCreatedAt()
                ));
    }

    public List<ChatMessageResponse> messages(
            UUID workspaceId,
            UUID userId,
            UUID conversationId
    ) throws Exception {
        Conversation conversation = conversationRepository
                .findByIdAndWorkspace_IdAndUserIdAndDeletedAtIsNull(
                        conversationId, workspaceId, userId
                )
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        return chatMessageRepository
                .findTop12ByConversation_IdOrderByCreatedAtDesc(conversation.getId())
                .stream()
                .sorted(java.util.Comparator.comparing(ChatMessage::getCreatedAt))
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    public void addMessage(
            Conversation conversation,
            ChatMessageRole role,
            String content
    ) {
        chatMessageRepository.save(ChatMessage.create(conversation, role, content));
    }

    public List<ChatMessage> recentHistory(Conversation conversation) {
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findTop12ByConversation_IdOrderByCreatedAtDesc(
                        conversation.getId()
                )
        );
        Collections.reverse(messages);
        return List.copyOf(messages);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID userId, UUID conversationId) throws Exception {
        Conversation conversation = conversationRepository
                .findByIdAndWorkspace_IdAndUserIdAndDeletedAtIsNull(
                        conversationId, workspaceId, userId
                )
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        chatMessageRepository.deleteAllByConversation_Id(conversationId);
        conversation.softDelete();
        conversationRepository.save(conversation);
        workspaceService.adjustChatCount(workspaceId, -1);
    }
}
