package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;

import java.io.File;

public interface MailService {

    // --- QUẢN LÝ TÀI KHOẢN HỘP THƯ (STALWART ADMIN API) ---
    ApiResponse<Void> createMailAccount(String username, String password, String fullName);
    ApiResponse<Void> deleteMailAccount(String username);

    // --- GỬI MAIL THÔNG BÁO TỰ ĐỘNG (SPRING JAVA MAIL SENDER / SMTP) ---
    ApiResponse<Void> sendSimpleEmail(String toEmail, String subject, String content);
    ApiResponse<Void> sendHtmlEmail(String toEmail, String subject, String htmlContent);
    ApiResponse<Void> sendEmailWithAttachment(String toEmail, String subject, String content, File attachment);
}