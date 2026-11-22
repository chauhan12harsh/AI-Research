package com.harsh.research.ai.retrieval;

import com.harsh.research.ai.retrieval.dto.AskRequest;
import com.harsh.research.ai.retrieval.dto.AskResponse;
import com.harsh.research.common.ApiResponse;
import com.harsh.research.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/ask")
public class RagController {

    private final RagAnswerService ragAnswerService;

    @PostMapping
    public ResponseEntity<ApiResponse<AskResponse>> ask(
            @PathVariable UUID workspaceId,
            @CurrentUser UUID userId,
            @Valid @RequestBody AskRequest request
    ) throws Exception {
        AskResponse response = ragAnswerService.ask(
                workspaceId, userId, request.conversationId(), request.question()
        );
        return ResponseEntity.ok(ApiResponse.Ok(response));
    }
}
