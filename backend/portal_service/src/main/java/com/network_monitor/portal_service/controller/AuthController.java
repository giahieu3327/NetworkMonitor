package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.ForgotPasswordRequest;
import com.network_monitor.portal_service.model.dto.request.LoginRequest;
import com.network_monitor.portal_service.model.dto.request.LogoutRequest;
import com.network_monitor.portal_service.model.dto.request.RefreshTokenRequest;
import com.network_monitor.portal_service.model.dto.request.ResetPasswordRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;
import com.network_monitor.portal_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    // ============================================================
    // 1. ĐĂNG NHẬP
    // Public
    // ============================================================

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {

        ApiResponse<TokenResponse> response =
                authService.login(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 2. REFRESH ACCESS TOKEN
    // Public
    // ============================================================

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {

        ApiResponse<TokenResponse> response =
                authService.refreshToken(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 3. ĐĂNG XUẤT
    // Yêu cầu đăng nhập
    // ============================================================

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {

        ApiResponse<Void> response =
                authService.logout(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 4. QUÊN MẬT KHẨU
    // Public
    //
    // Người dùng nhập email.
    // Backend kiểm tra email trong Keycloak
    // rồi gửi OTP qua email.
    // ============================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {

        ApiResponse<Void> response =
                authService.forgotPassword(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 5. ĐẶT LẠI MẬT KHẨU
    // Public
    //
    // Sau khi xác nhận OTP thành công,
    // frontend gửi email + mật khẩu mới.
    // ============================================================

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {

        ApiResponse<Void> response =
                authService.resetPassword(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }
}