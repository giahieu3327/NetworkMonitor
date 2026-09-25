package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.*;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final KeyCloakService keyCloakService;
    private final UserRepository userRepository;

    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    public boolean existsById(String id) {
        return userRepository.existsById(id);
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User createLocalUser(String keycloakId, String username, String email, String fullName, String phoneNumber) {
        User user = User.builder()
                .id(keycloakId)
                .username(username)
                .email(email)
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    public UserProfileResponse getCurrentUserProfile(String userId, List<String> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tài khoản trong cơ sở dữ liệu!"));

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .isActive(user.getIsActive())
                .roles(roles)
                .build();
    }

    public Page<User> getUsers(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return userRepository.searchUsers(keyword.trim(), pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateUserStatus(String userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        // 1. Cập nhật trạng thái trên Keycloak trước
        keyCloakService.setUserEnabled(userId, isActive);

        // 2. Cập nhật trạng thái ở Postgres DB
        user.setIsActive(isActive);
        return userRepository.save(user);
    }

    @Transactional
    public void resetUserPassword(String userId, String newPassword) {
        // 1. Kiểm tra user có tồn tại trong DB Postgres không
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        // 2. Đặt lại mật khẩu trên Keycloak
        keyCloakService.setPassword(userId, newPassword, false);
        log.info("Admin đã đặt lại mật khẩu cho tài khoản: {}", user.getUsername());
    }

    @Transactional
    public void deleteUser(String userId, String currentUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        // Ngăn chặn xóa tài khoản admin gốc
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("Không được phép xóa tài khoản Admin tối cao (username: admin)!");
        }

        // 1. Xóa trên Keycloak trước
        keyCloakService.deleteUser(userId);

        // 2. Xóa trong cơ sở dữ liệu Postgres
        userRepository.delete(user);
        log.info("Admin [{}] đã xóa thành công tài khoản [{}]", currentUsername, user.getUsername());
    }

    @Transactional
    public User updateUserProfile(String userId, String email, String fullName, String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        // Cập nhật Keycloak
        keyCloakService.updateKeycloakUser(userId, email, fullName);

        // Cập nhật Postgres
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);

        return userRepository.save(user);
    }

    @Transactional
    public void updateUserRole(String userId, String newRole, String currentUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        if (ROLE_SUPER_ADMIN.equalsIgnoreCase(newRole) && !"admin".equalsIgnoreCase(currentUsername)) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ duy nhất Admin tối cao mới được gán ROLE_SUPER_ADMIN!");
        }

        keyCloakService.updateUserRole(userId, newRole);
    }
}