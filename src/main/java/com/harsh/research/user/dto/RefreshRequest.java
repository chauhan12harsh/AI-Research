package com.harsh.research.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 256, message = "Refresh token is invalid")
        String refreshToken
) {}
