package com.harsh.research.user;

import com.harsh.research.common.ApiResponse;
import com.harsh.research.user.dto.AuthResponse;
import com.harsh.research.user.dto.LoginRequest;
import com.harsh.research.user.dto.RegisterRequest;
import com.harsh.research.user.dto.RefreshRequest;
import com.harsh.research.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) throws  Exception{

        AuthResponse authResponse = authService.register(request);

        return ResponseEntity.ok(
                ApiResponse.Ok(
                        authResponse
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) throws  Exception{

        AuthResponse authResponse = authService.login(request);

        return ResponseEntity.ok(
                ApiResponse.Ok(
                        authResponse
                )
        );
    }


    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.Ok(
                authService.refresh(request.refreshToken())
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshRequest request
    ) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.Ok(null));
    }

}
