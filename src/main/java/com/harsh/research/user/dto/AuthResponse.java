package com.harsh.research.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserResponse user
) {}
