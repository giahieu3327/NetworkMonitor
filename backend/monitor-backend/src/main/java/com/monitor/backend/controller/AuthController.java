package com.monitor.backend.controller;

import com.monitor.backend.model.dto.*;
import com.monitor.backend.model.entity.User;
import com.monitor.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<User> register(
            @Valid @RequestBody RegisterRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String currentUsername = jwt.getClaimAsString("preferred_username");
        User registeredUser = authService.register(request, currentUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.refreshToken(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        UserProfileResponse userProfile = authService.getMe(jwt);
        return ResponseEntity.ok(userProfile);
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt,
            @jakarta.validation.Valid @RequestBody com.monitor.backend.model.dto.ChangePasswordRequest request
    ) {
        authService.changePassword(jwt, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.monitor.backend.model.entity.User> updateOwnProfile(
            @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt,
            @jakarta.validation.Valid @RequestBody com.monitor.backend.model.dto.UpdateUserProfileRequest request
    ) {
        String userId = jwt.getSubject();
        com.monitor.backend.model.entity.User updatedUser = userService.updateUserProfile(
                userId, request.getEmail(), request.getFullName(), request.getPhoneNumber()
        );
        return ResponseEntity.ok(updatedUser);
    }
}