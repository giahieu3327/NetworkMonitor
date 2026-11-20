package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // =========================================================
    // REGISTRATION
    // =========================================================

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {

        ApiResponse<Void> response = authService.register(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // USER IMPORT / EXPORT
    // =========================================================

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importUsers(
            @RequestParam("file") MultipartFile file) {

        ApiResponse<Map<String, Object>> response =
                authService.importUsersFromExcel(file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportUsers() throws IOException {
        byte[] file = authService.exportUsers();

        return downloadFile(file, "users.xlsx", EXCEL_CONTENT_TYPE);
    }

    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadUserImportTemplate()
            throws IOException {

        byte[] file = authService.downloadUserImportTemplate();

        return downloadFile(
                file,
                "users-import-template.xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    // =========================================================
    // AUTHENTICATION
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        ApiResponse<TokenResponse> response = authService.login(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {

        ApiResponse<TokenResponse> response = authService.refresh(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request) {

        ApiResponse<Void> response = authService.logout(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // PASSWORD RECOVERY
    // =========================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        ApiResponse<Void> response = authService.forgotPassword(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-reset-password-otp")
    public ResponseEntity<ApiResponse<String>> verifyResetPasswordOtp(
            @Valid @RequestBody VerifyResetPasswordOtpRequest request) {

        ApiResponse<String> response =
                authService.verifyResetPasswordOtp(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        ApiResponse<Void> response = authService.resetPassword(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // EMAIL VERIFICATION
    // =========================================================

    @PostMapping("/send-verification-email")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
            @Valid @RequestBody SendVerificationEmailRequest request) {

        ApiResponse<Void> response =
                authService.sendVerificationEmail(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        ApiResponse<String> response = authService.verifyEmail(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // COMMON DOWNLOAD HELPER
    // =========================================================

    private ResponseEntity<byte[]> downloadFile(
            byte[] file,
            String filename,
            String contentType) {

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(file);
    }
}