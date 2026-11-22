package com.harsh.research.user;

import com.harsh.research.common.ApiResponse;
import com.harsh.research.security.CurrentUser;
import com.harsh.research.user.dto.UserResponse;
import com.harsh.research.user.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(@CurrentUser UUID userId) throws  Exception{

        User user = userService.getById(userId);
        UserResponse userResponse = UserResponse.from(user);

        return ResponseEntity.ok(
                ApiResponse.Ok(
                        userResponse
                )
        );
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @CurrentUser UUID userId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.Ok(null));
    }
}
