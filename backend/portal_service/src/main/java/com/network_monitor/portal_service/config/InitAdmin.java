package com.network_monitor.portal_service.config;


import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InitAdmin implements CommandLineRunner {

    private final KeyCloakService keyCloakService;
    private final UserService userService;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.full-name}")
    private String adminFullName;

    @Value("${app.admin.phone}")
    private String adminPhone;

    @Value("${app.admin.role}")
    private String adminRole;

    @Override
    public void run(String... args) {
        try {
            initAdminAccount();
        } catch (Exception e) {
            log.error("Lỗi trong quá trình khởi tạo tài khoản Admin mặc định: {}", e.getMessage(), e);
        }
    }

    private void initAdminAccount() {
        boolean keycloakExists = checkKeycloakAdminExists();
        boolean postgresExists = checkPostgresAdminExists();

        if (keycloakExists && postgresExists) {
            log.info("Tài khoản Admin đã tồn tại đầy đủ trên cả Keycloak và Postgres. Bỏ qua khởi tạo.");
            return;
        }

        String keycloakUserId = syncKeycloakAdmin(keycloakExists);
        syncPostgresAdmin(postgresExists, keycloakUserId);
    }

    private boolean checkKeycloakAdminExists() {
        return keyCloakService.existsByUsername(adminUsername);
    }

    private boolean checkPostgresAdminExists() {
        return userService.existsByUsername(adminUsername);
    }

    private String syncKeycloakAdmin(boolean exists) {
        if (exists) {
            log.info("Tài khoản Admin đã có trên Keycloak. Lấy Keycloak ID...");
            Optional<String> userIdOpt = keyCloakService.findUserIdByUsername(adminUsername);
            return userIdOpt.orElseThrow(() -> new IllegalStateException("Không tìm thấy UUID Keycloak cho user " + adminUsername));
        }

        log.info("Tạo tài khoản Admin mới trên Keycloak...");
        return keyCloakService.createAndConfigureUser(
                adminUsername,
                adminEmail,
                adminFullName,
                adminPassword,
                adminRole
        );
    }

    private void syncPostgresAdmin(boolean exists, String keycloakUserId) {
        if (exists) {
            log.info("Tài khoản Admin đã có trong cơ sở dữ liệu Postgres.");
            return;
        }

        log.info("Tạo tài khoản Admin trong cơ sở dữ liệu Postgres với Keycloak UUID: {}", keycloakUserId);
        userService.createLocalUser(
                keycloakUserId,
                adminUsername,
                adminEmail,
                adminFullName,
                adminPhone
        );
        log.info("Tạo tài khoản Admin mặc định hoàn tất!");
    }
}