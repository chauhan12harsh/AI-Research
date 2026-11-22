package com.harsh.research.ai.retrieval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AskRequest(
        UUID conversationId,

        @NotBlank(message = "Question is required")
        @Size(max = 4000, message = "Question must not exceed 4000 characters")
        String question
) {}
