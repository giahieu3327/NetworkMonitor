package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.ForgotPasswordRequest;
import com.network_monitor.portal_service.model.dto.request.LoginRequest;
import com.network_monitor.portal_service.model.dto.request.LogoutRequest;
import com.network_monitor.portal_service.model.dto.request.RefreshTokenRequest;
import com.network_monitor.portal_service.model.dto.request.ResetPasswordRequest;
import com.network_monitor.portal_service.model.dto.request.SendPasswordResetOtpEmailRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;
import com.network_monitor.portal_service.service.AuthService;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.MailService;

import lombok.RequiredArgsConstructor;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final KeyCloakService keyCloakService;

    private final MailService mailService;


    // ============================================================
    // LOGIN
    // ============================================================

    @Override
    public ApiResponse<TokenResponse> login(
            LoginRequest request
    ) {

        return keyCloakService.login(
                request.getUsername(),
                request.getPassword()
        );
    }


    // ============================================================
    // REFRESH TOKEN
    // ============================================================

    @Override
    public ApiResponse<TokenResponse> refreshToken(
            RefreshTokenRequest request
    ) {

        return keyCloakService.refreshToken(
                request.getRefreshToken()
        );
    }


    // ============================================================
    // LOGOUT
    // ============================================================

    @Override
    public ApiResponse<Void> logout(
            LogoutRequest request
    ) {

        return keyCloakService.logout(
                request.getRefreshToken()
        );
    }


    // ============================================================
    // FORGOT PASSWORD
    // ============================================================

    @Override
    public ApiResponse<Void> forgotPassword(
            ForgotPasswordRequest request
    ) {

        /*
         * Bước 1:
         * Tìm user trên Keycloak bằng email.
         */

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(
                        request.getEmail()
                );

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {

            return ApiResponse.error(
                    "Không thể thực hiện yêu cầu",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        UserRepresentation user =
                userResponse.getData();

        /*
         * Bước 2:
         * Gửi OTP thông qua MailService.
         */

        SendPasswordResetOtpEmailRequest emailRequest =
                SendPasswordResetOtpEmailRequest.builder()
                        .toEmail(user.getEmail())
                        .userName(user.getUsername())
                        .build();

        return mailService.sendPasswordResetOtpEmail(
                emailRequest
        );
    }


    // ============================================================
    // RESET PASSWORD
    // ============================================================

    @Override
    public ApiResponse<Void> resetPassword(
            ResetPasswordRequest request
    ) {

        /*
         * Bước 1:
         * Kiểm tra mật khẩu xác nhận.
         */

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

            return ApiResponse.error(
                    "Đặt lại mật khẩu thất bại",
                    "Mật khẩu xác nhận không khớp!"
            );
        }


        /*
         * Bước 2:
         * Tìm user trên Keycloak bằng email.
         */

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(
                        request.getEmail()
                );

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {

            return ApiResponse.error(
                    "Đặt lại mật khẩu thất bại",
                    "Email không tồn tại trong hệ thống!"
            );
        }


        /*
         * Bước 3:
         * Lấy Keycloak User ID.
         */

        String userId =
                userResponse.getData().getId();


        /*
         * Bước 4:
         * Cập nhật password trên Keycloak.
         */

        return keyCloakService.setPassword(
                userId,
                request.getNewPassword(),
                false
        );
    }
}