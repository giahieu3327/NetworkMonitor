
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.ForgotPasswordRequest;
import com.network_monitor.portal_service.model.dto.request.LoginRequest;
import com.network_monitor.portal_service.model.dto.request.LogoutRequest;
import com.network_monitor.portal_service.model.dto.request.RefreshRequest;
import com.network_monitor.portal_service.model.dto.request.RegisterRequest;
import com.network_monitor.portal_service.model.dto.request.ResetPasswordRequest;
import com.network_monitor.portal_service.model.dto.request.SendVerificationEmailRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyEmailRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyResetPasswordOtpRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface AuthService {

    // Registration
    ApiResponse<Void> register(RegisterRequest request);

    // Excel import
    ApiResponse<Map<String, Object>> importUsersFromExcel(
            MultipartFile file
    );

    // Excel export
    byte[] exportUsers() throws IOException;

    // Download Excel import template
    byte[] downloadUserImportTemplate() throws IOException;

    // Authentication
    ApiResponse<TokenResponse> login(LoginRequest request);

    ApiResponse<TokenResponse> refresh(RefreshRequest request);

    ApiResponse<Void> logout(LogoutRequest request);

    // Password recovery
    ApiResponse<Void> forgotPassword(ForgotPasswordRequest request);

    ApiResponse<String> verifyResetPasswordOtp(
            VerifyResetPasswordOtpRequest request
    );

    ApiResponse<Void> resetPassword(ResetPasswordRequest request);

    // Email verification
    ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    );

    ApiResponse<String> verifyEmail(VerifyEmailRequest request);
}
