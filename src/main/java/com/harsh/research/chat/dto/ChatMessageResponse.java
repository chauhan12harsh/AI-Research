package com.harsh.research.chat.dto;

import com.harsh.research.chat.ChatMessageRole;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        ChatMessageRole role,
        String content,
        Instant createdAt
) {}
