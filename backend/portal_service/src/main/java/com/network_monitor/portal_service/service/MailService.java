package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.SendMailRequest;
import com.network_monitor.portal_service.model.dto.request.SendPasswordResetOtpEmailRequest;
import com.network_monitor.portal_service.model.dto.request.SendVerificationEmailRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyEmailTokenRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyPasswordResetOtpRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;

import java.io.InputStream;

public interface MailService {

    // 1. Gửi email text
    ApiResponse<Void> sendTextEmail(
            SendMailRequest request
    );

    // 2. Gửi email HTML
    ApiResponse<Void> sendHtmlEmail(
            SendMailRequest request
    );

    // 3. Gửi email kèm file
    ApiResponse<Void> sendEmailWithAttachment(
            SendMailRequest request,
            String fileName,
            InputStream fileInputStream
    );

    // 4. Gửi email xác thực
    ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    );

    // 5. Xác thực email bằng token
    ApiResponse<String> verifyEmailToken(
            VerifyEmailTokenRequest request
    );

    // 6. Gửi OTP reset password
    ApiResponse<Void> sendPasswordResetOtpEmail(
            SendPasswordResetOtpEmailRequest request
    );

    // 7. Xác thực OTP reset password
    ApiResponse<String> verifyPasswordResetOtp(
            VerifyPasswordResetOtpRequest request
    );
}