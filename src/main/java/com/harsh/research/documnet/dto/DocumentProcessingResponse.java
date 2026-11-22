package com.harsh.research.documnet.dto;

import java.time.Instant;

public record DocumentProcessingResponse(
        String jobStatus,
        int attempts,
        Instant nextAttemptAt,
        String lastError
) {}
