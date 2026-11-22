package com.harsh.research.workspace;

import com.harsh.research.documnet.Document;
import com.harsh.research.documnet.DocumentCleanupService;
import com.harsh.research.documnet.DocumentDeletionService;
import com.harsh.research.documnet.DocumentRepository;
import com.harsh.research.chat.ChatMessageRepository;
import com.harsh.research.chat.Conversation;
import com.harsh.research.chat.ConversationRepository;
import com.harsh.research.workspace.dto.CreateWorkspaceRequest;
import com.harsh.research.workspace.dto.UpdateWorkspaceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final DocumentCleanupService documentCleanupService;
    private final DocumentDeletionService documentDeletionService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public Workspace create(UUID ownerId, CreateWorkspaceRequest request) throws Exception {
        if (workspaceRepository.existsByOwnerAndName(ownerId, request.name())) {
            throw new Exception("Workspace already exists with this name");
        }

        Workspace workspace = Workspace.create(
                ownerId, request.name().trim(), request.description()
        );
        return workspaceRepository.save(workspace);
    }

    public List<Workspace> listOfOwner(UUID ownerId) {
        return workspaceRepository.findAllByOwnerIdAndDeletedAtNullOrderByCreatedAtDesc(ownerId);
    }

    public Workspace get(UUID workspaceId, UUID ownerId) throws Exception {
        return workspaceRepository.findByIdAndOwnerIdAndDeletedAtNull(workspaceId, ownerId)
                .orElseThrow(() -> new Exception("Workspace not found"));
    }

    public Workspace update(
            UUID workspaceId,
            UUID ownerId,
            UpdateWorkspaceRequest request
    ) throws Exception {
        Workspace workspace = get(workspaceId, ownerId);

        if (request.name() != null && !request.name().isBlank()
                && !request.name().trim().equalsIgnoreCase(workspace.getName())) {
            if (workspaceRepository.existsByOwnerAndName(ownerId, request.name().trim())) {
                throw new Exception("Workspace already exists with this name");
            }
            workspace.setName(request.name().trim());
        }

        if (request.description() != null) {
            workspace.setDescription(request.description());
        }

        return workspaceRepository.save(workspace);
    }

    public void delete(UUID workspaceId, UUID ownerId) throws Exception {
        Workspace workspace = workspaceRepository.findByIdAndOwnerId(workspaceId, ownerId)
                .orElseThrow(() -> new Exception("Workspace not found"));

        // Hide the workspace first so new uploads cannot reserve quota. Repeated
        // DELETE requests still find the soft-deleted row and resume cleanup.
        if (!workspace.isDeleted()) {
            workspace.softDelete();
            workspaceRepository.save(workspace);
        }

        List<Document> documents =
                documentRepository.findAllByWorkspace_IdAndDeletedAtIsNull(workspaceId);

        for (Document document : documents) {
            Document deleting = documentDeletionService.markDeleting(document.getId());
            documentCleanupService.cleanup(deleting);
            deleting.softDelete();
            documentRepository.save(deleting);
        }

        List<Conversation> conversations =
                conversationRepository.findAllByWorkspace_IdAndDeletedAtIsNull(workspaceId);

        for (Conversation conversation : conversations) {
            chatMessageRepository.deleteAllByConversation_Id(conversation.getId());
            conversation.softDelete();
            conversationRepository.save(conversation);
        }

        if (!conversations.isEmpty()) {
            adjustChatCount(workspaceId, -conversations.size());
        }
    }

    @Transactional
    public boolean reserveDocumentStorage(
            UUID workspaceId,
            long additionalBytes,
            long workspaceLimit
    ) {
        if (additionalBytes <= 0 || additionalBytes > workspaceLimit) {
            return false;
        }

        return workspaceRepository.reserveDocumentStorage(
                workspaceId,
                additionalBytes,
                workspaceLimit - additionalBytes
        ) == 1;
    }

    @Transactional
    public void adjustChatCount(UUID workspaceId, int delta) throws Exception {
        if (workspaceRepository.adjustChatCounter(workspaceId, delta) != 1) {
            throw new Exception("Workspace not found");
        }
    }

    @Transactional
    public void adjustCounters(
            UUID workspaceId,
            int docDelta,
            long bytesDelta
    ) throws Exception {
        if (workspaceRepository.adjustUsageCounters(
                workspaceId, docDelta, bytesDelta
        ) != 1) {
            throw new Exception("Workspace not found");
        }
    }
}
