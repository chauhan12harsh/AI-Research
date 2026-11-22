package com.harsh.research.user;

import com.harsh.research.common.ConflictException;
import com.harsh.research.security.JwtService;
import com.harsh.research.user.dto.AuthResponse;
import com.harsh.research.user.dto.LoginRequest;
import com.harsh.research.user.dto.RegisterRequest;
import com.harsh.research.user.dto.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) throws Exception {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("User with this email already exists");
        }

        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }

        if (CommonPasswords.LIST.stream()
                .anyMatch(common -> common.equalsIgnoreCase(request.password()))) {
            throw new IllegalArgumentException("That password is too common. Please choose another one");
        }

        User user = User.register(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim()
        );

        try {
            // Flush here so the unique-email constraint is checked inside this
            // method rather than later at transaction commit.
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("User with this email already exists");
        }

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) throws Exception {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid username or password")
                );

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (user.getStatus() == UserStatus.SUSPEND) {
            throw new org.springframework.security.authentication.DisabledException("This account has been suspended");
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return new AuthResponse(token, UserResponse.from(user));
    }
}
