package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.SendMailRequest;
import com.network_monitor.portal_service.model.dto.request.SendPasswordResetOtpEmailRequest;
import com.network_monitor.portal_service.model.dto.request.SendVerificationEmailRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyEmailTokenRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyPasswordResetOtpRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.service.MailService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    // ============================================================
    // 1. SEND TEXT EMAIL
    // ============================================================

    @PostMapping("/send-text")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> sendTextEmail(
            @Valid @RequestBody SendMailRequest request
    ) {

        ApiResponse<Void> response =
                mailService.sendTextEmail(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 2. SEND HTML EMAIL
    // ============================================================

    @PostMapping("/send-html")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> sendHtmlEmail(
            @Valid @RequestBody SendMailRequest request
    ) {

        ApiResponse<Void> response =
                mailService.sendHtmlEmail(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 3. SEND EMAIL WITH ATTACHMENT
    // ============================================================

    @PostMapping(
            value = "/send-with-attachment",
            consumes = "multipart/form-data"
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> sendEmailWithAttachment(
            @RequestPart("request")
            @Valid SendMailRequest request,

            @RequestPart("file")
            MultipartFile file
    ) {

        if (file == null || file.isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.error(
                                    "File đính kèm không được để trống."
                            )
                    );
        }

        try {

            ApiResponse<Void> response =
                    mailService.sendEmailWithAttachment(
                            request,
                            file.getOriginalFilename(),
                            file.getInputStream()
                    );

            if (!response.isSuccess()) {
                return ResponseEntity
                        .badRequest()
                        .body(response);
            }

            return ResponseEntity.ok(response);

        } catch (IOException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.error(
                                    "Không thể đọc file đính kèm."
                            )
                    );
        }
    }


    // ============================================================
    // 4. SEND VERIFICATION EMAIL
    // ============================================================

    @PostMapping("/send-verification-email")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
            @Valid @RequestBody SendVerificationEmailRequest request
    ) {

        ApiResponse<Void> response =
                mailService.sendVerificationEmail(request);

        if (!response.isSuccess()) {
            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 5. VERIFY EMAIL TOKEN
    // ============================================================

    @PostMapping("/verify-verification-email")
    public ResponseEntity<ApiResponse<String>> verifyEmailToken(
            @Valid @RequestBody VerifyEmailTokenRequest request
    ) {

        ApiResponse<String> response =
                mailService.verifyEmailToken(request);

        if (!response.isSuccess()) {
            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 6. SEND PASSWORD RESET OTP
    // ============================================================

    @PostMapping("/send-reset-password-otp")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetOtpEmail(
            @Valid @RequestBody SendPasswordResetOtpEmailRequest request
    ) {

        ApiResponse<Void> response =
                mailService.sendPasswordResetOtpEmail(request);

        if (!response.isSuccess()) {
            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // 7. VERIFY PASSWORD RESET OTP
    // ============================================================

    @PostMapping("/verify-reset-password-otp")
    public ResponseEntity<ApiResponse<String>> verifyPasswordResetOtp(
            @Valid @RequestBody VerifyPasswordResetOtpRequest request
    ) {

        ApiResponse<String> response =
                mailService.verifyPasswordResetOtp(request);

        if (!response.isSuccess()) {
            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }
}
