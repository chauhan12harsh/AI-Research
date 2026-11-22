package com.harsh.research.user;

import com.harsh.research.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RefreshTokenServiceIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void rotatesRefreshTokensAndLogoutRevokesAllAccessSessions() {
        String email = "refresh-" + java.util.UUID.randomUUID() + "@example.test";
        User user = userRepository.saveAndFlush(
                User.register(email, "not-used-by-this-test", "Refresh Test")
        );

        RefreshTokenService.IssuedTokens first = refreshTokenService.issue(user);
        assertNotNull(first.refreshToken());
        assertEquals(0, jwtService.parse(first.accessToken()).orElseThrow().tokenVersion());

        RefreshTokenService.IssuedTokens second =
                refreshTokenService.rotate(first.refreshToken());

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertThrows(
                BadCredentialsException.class,
                () -> refreshTokenService.rotate(first.refreshToken())
        );

        refreshTokenService.revoke(second.refreshToken());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(1, reloaded.getTokenVersion());

        assertThrows(
                BadCredentialsException.class,
                () -> refreshTokenService.rotate(second.refreshToken())
        );
    }
}
