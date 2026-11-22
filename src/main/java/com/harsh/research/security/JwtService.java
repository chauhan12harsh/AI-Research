package com.harsh.research.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${app.security.jwt-secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt-secret must be configured");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "app.security.jwt-secret must be a Base64-encoded key",
                    exception
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT signing key must contain at least 256 bits"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return generateAccessToken(userId, email, role, 0);
    }

    public String generateAccessToken(
            UUID userId,
            String email,
            String role,
            int tokenVersion
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("ver", tokenVersion)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(signingKey)
                .compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            Number versionClaim = claims.get("ver", Number.class);
            int tokenVersion = versionClaim == null ? 0 : versionClaim.intValue();

            if (email == null || role == null || role.isBlank() || tokenVersion < 0) {
                return Optional.empty();
            }

            return Optional.of(new AuthPrincipal(userId, email, role, tokenVersion));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
