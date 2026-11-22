package com.harsh.research.chat;

import com.harsh.research.chat.dto.ChatMessageResponse;
import com.harsh.research.chat.dto.ConversationResponse;
import com.harsh.research.common.ApiResponse;
import com.harsh.research.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConversationResponse>>> list(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId,
            Pageable pageable
    ) throws Exception {
        return ResponseEntity.ok(ApiResponse.Ok(
                conversationService.list(workspaceId, userId, pageable)
        ));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> messages(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @CurrentUser UUID userId
    ) throws Exception {
        return ResponseEntity.ok(ApiResponse.Ok(
                conversationService.messages(workspaceId, userId, conversationId)
        ));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @CurrentUser UUID userId
    ) throws Exception {
        conversationService.delete(workspaceId, userId, conversationId);
        return ResponseEntity.ok(ApiResponse.Ok(null));
    }
}
