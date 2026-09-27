package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.service.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    @Value("${stalwart.api-url:http://localhost:8080}")
    private String apiUrl;

    @Value("${stalwart.admin-user:admin@monitor.com}")
    private String adminUser;

    @Value("${stalwart.admin-password:Admin@123}")
    private String adminPassword;

    @Value("${stalwart.domain:monitor.com}")
    private String mailDomain;

    @Value("${stalwart.system-sender:no-reply@monitor.com}")
    private String systemSender;

    private final JavaMailSender javaMailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    // =========================================================================
    // 1. QUẢN LÝ TÀI KHOẢN STALWART MAIL SERVER (DÙNG STALWART ADMIN REST API)
    // =========================================================================

    @Override
    public ApiResponse<Void> createMailAccount(String username, String password, String fullName) {
        String url = apiUrl + "/api/v1/accounts";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(adminUser, adminPassword);

        String fullEmail = username + "@" + mailDomain;

        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        body.put("email", fullEmail);
        body.put("password", password);
        body.put("fullName", fullName);
        body.put("type", "individual");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Tạo tài khoản email Stalwart thành công cho user: {}", fullEmail);
                return ApiResponse.success("Tạo tài khoản email thành công");
            }
            log.error("Tạo tài khoản email Stalwart thất bại cho user {}. Status: {}", fullEmail, response.getStatusCode());
            return ApiResponse.error("Tạo tài khoản email thất bại", "Mã trạng thái Stalwart: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Lỗi khi gọi API tạo email Stalwart cho user {}: {}", username, e.getMessage());
            return ApiResponse.error("Tạo tài khoản email Stalwart thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> deleteMailAccount(String username) {
        String url = apiUrl + "/api/v1/accounts/" + username;

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(adminUser, adminPassword);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
            log.info("Xóa tài khoản email Stalwart thành công cho user: {}", username);
            return ApiResponse.success("Xóa tài khoản email thành công");
        } catch (Exception e) {
            log.error("Lỗi xóa tài khoản email Stalwart {}: {}", username, e.getMessage());
            return ApiResponse.error("Xóa tài khoản email Stalwart thất bại", e.getMessage());
        }
    }

    // =========================================================================
    // 2. TÍNH NĂNG GỬI MAIL THÔNG BÁO TỰ ĐỘNG (SỬ DỤNG SENDER no-reply@monitor.com)
    // =========================================================================

    @Override
    public ApiResponse<Void> sendSimpleEmail(String toEmail, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(systemSender);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);

            javaMailSender.send(message);
            log.info("Gửi simple email thành công từ [{}] đến [{}]", systemSender, toEmail);
            return ApiResponse.success("Gửi email thành công");
        } catch (Exception e) {
            log.error("Lỗi gửi simple email từ [{}] đến [{}]: {}", systemSender, toEmail, e.getMessage());
            return ApiResponse.error("Gửi email thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(systemSender);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            javaMailSender.send(message);
            log.info("Gửi HTML email thành công từ [{}] đến [{}]", systemSender, toEmail);
            return ApiResponse.success("Gửi HTML email thành công");
        } catch (Exception e) {
            log.error("Lỗi gửi HTML email từ [{}] đến [{}]: {}", systemSender, toEmail, e.getMessage());
            return ApiResponse.error("Gửi HTML email thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> sendEmailWithAttachment(String toEmail, String subject, String content, File attachment) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(systemSender);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, false);

            if (attachment != null && attachment.exists()) {
                helper.addAttachment(attachment.getName(), attachment);
            }

            javaMailSender.send(message);
            log.info("Gửi email đính kèm thành công từ [{}] đến [{}]", systemSender, toEmail);
            return ApiResponse.success("Gửi email đính kèm file thành công");
        } catch (Exception e) {
            log.error("Lỗi gửi email đính kèm từ [{}] đến [{}]: {}", systemSender, toEmail, e.getMessage());
            return ApiResponse.error("Gửi email đính kèm file thất bại", e.getMessage());
        }
    }
}