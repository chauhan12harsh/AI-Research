package com.harsh.research.security;

import java.util.UUID;

public record AuthPrincipal(UUID userId, String email, String role, int tokenVersion) {
    public AuthPrincipal(UUID userId, String email, String role) {
        this(userId, email, role, 0);
    }
}
