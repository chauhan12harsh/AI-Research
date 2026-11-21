package com.harsh.research.documnet.dto;

import java.util.UUID;

public record DocumentContentResponse(
       String content,
        int offset,
        int limit,
       int totalLength
) {
}
