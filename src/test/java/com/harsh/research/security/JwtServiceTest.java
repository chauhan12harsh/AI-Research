package com.harsh.research.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void generatesAndParsesAccessToken() {
        JwtService service = new JwtService(SECRET);
        UUID userId = UUID.randomUUID();

        String token = service.generateAccessToken(
                userId, "research@example.com", "USER"
        );

        AuthPrincipal principal = service.parse(token).orElseThrow();
        assertEquals(userId, principal.userId());
        assertEquals("research@example.com", principal.email());
        assertEquals("USER", principal.role());
    }

    @Test
    void preservesTokenVersionForRevocationChecks() {
        JwtService service = new JwtService(SECRET);
        UUID userId = UUID.randomUUID();

        String token = service.generateAccessToken(
                userId, "research@example.com", "USER", 7
        );

        assertEquals(7, service.parse(token).orElseThrow().tokenVersion());
    }

    @Test
    void rejectsTokenSignedByDifferentKey() {
        JwtService issuer = new JwtService(SECRET);
        JwtService verifier = new JwtService(
                Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5,
                        6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32})
        );

        String token = issuer.generateAccessToken(
                UUID.randomUUID(), "research@example.com", "USER"
        );

        assertTrue(verifier.parse(token).isEmpty());
    }

    @Test
    void rejectsWeakSigningKeys() {
        String weakKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new JwtService(weakKey));
    }

    @Test
    void rejectsMalformedTokens() {
        assertTrue(new JwtService(SECRET).parse("not-a-jwt").isEmpty());
    }
}
