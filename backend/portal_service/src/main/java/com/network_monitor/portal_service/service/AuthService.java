package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;

public interface AuthService {

    ApiResponse<Void> register(
            RegisterRequest request
    );

    ApiResponse<TokenResponse> login(
            LoginRequest request
    );

    ApiResponse<TokenResponse> refresh(
            RefreshRequest request
    );

    ApiResponse<Void> logout(
            LogoutRequest request
    );

    ApiResponse<Void> forgotPassword(
            ForgotPasswordRequest request
    );

    ApiResponse<String> verifyResetPasswordOtp(
            VerifyResetPasswordOtpRequest request
    );

    ApiResponse<Void> resetPassword(
            ResetPasswordRequest request
    );

    ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    );

    ApiResponse<String> verifyEmail(
            VerifyEmailRequest request
    );
}