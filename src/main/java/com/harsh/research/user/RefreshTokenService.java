package com.harsh.research.user;

import com.harsh.research.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public record IssuedTokens(UUID userId, String accessToken, String refreshToken, long expiresInSeconds) {}

    @Transactional
    public IssuedTokens issue(User user) {
        byte[] randomBytes = new byte[48];
        RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        refreshTokenRepository.save(RefreshToken.create(
                user.getId(),
                sha256(rawToken),
                Instant.now().plus(REFRESH_TTL)
        ));

        return new IssuedTokens(
                user.getId(),
                jwtService.generateAccessToken(
                        user.getId(), user.getEmail(), user.getRole().name()
                ),
                rawToken,
                Duration.ofHours(1).toSeconds()
        );
    }

    @Transactional
    public IssuedTokens rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw invalidRefreshToken();
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHashForUpdate(sha256(rawToken))
                .orElseThrow(this::invalidRefreshToken);

        Instant now = Instant.now();
        if (stored.isRevoked() || stored.isExpired(now)) {
            throw invalidRefreshToken();
        }

        User user = userRepository.findById(stored.getUserId())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::invalidRefreshToken);

        stored.revoke(now);
        refreshTokenRepository.save(stored);

        return issue(user);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            return;
        }

        refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken))
                .ifPresent(token -> {
                    token.revoke(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Scheduled(fixedDelayString = "${app.security.refresh-token-cleanup-ms:86400000}")
    public void removeExpiredTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(
                Instant.now().minus(Duration.ofDays(30))
        );
    }

    private String sha256(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private BadCredentialsException invalidRefreshToken() {
        return new BadCredentialsException("Refresh token is invalid or expired");
    }
}
