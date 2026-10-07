package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;

import java.io.InputStream;

public interface MailService {

    ApiResponse<Void> sendTextEmail(SendMailRequest request);

    ApiResponse<Void> sendHtmlEmail(SendMailRequest request);

    ApiResponse<Void> sendEmailWithAttachment(
            SendMailRequest request,
            String fileName,
            InputStream fileInputStream
    );

    ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    );

    ApiResponse<String> verifyEmailToken(
            VerifyEmailRequest request
    );

    ApiResponse<Void> sendPasswordResetOtpEmail(
            SendPasswordResetOtpEmailRequest request
    );

    ApiResponse<String> verifyPasswordResetOtp(
            VerifyResetPasswordOtpRequest request
    );
}