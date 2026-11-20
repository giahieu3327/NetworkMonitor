package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.UserMapper;
import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final KeyCloakService keyCloakService;

    @Override
    @Transactional
    public ApiResponse<Void> updateUser(
            String id,
            UserUpdateRequest request
    ) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + id));

            // 1. Kiểm tra thay đổi Username
            boolean usernameChanged = request.getUsername() != null
                    && !request.getUsername().isBlank()
                    && !request.getUsername().equals(user.getUsername());

            if (usernameChanged) {
                if (userRepository.existsByUsername(request.getUsername())) {
                    return ApiResponse.error(
                            "Cập nhật người dùng thất bại",
                            "Username đã tồn tại!"
                    );
                }
            }

            // 2. Kiểm tra thay đổi Email
            boolean emailChanged = request.getEmail() != null
                    && !request.getEmail().isBlank()
                    && !request.getEmail().equalsIgnoreCase(user.getEmail());

            if (emailChanged) {
                if (userRepository.existsByEmail(request.getEmail())) {
                    return ApiResponse.error(
                            "Cập nhật người dùng thất bại",
                            "Email đã tồn tại!"
                    );
                }
            }

            // 3. Kiểm tra Mật khẩu mới
            if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
                if (request.getConfirmNewPassword() == null || request.getConfirmNewPassword().isBlank()) {
                    return ApiResponse.error(
                            "Cập nhật người dùng thất bại",
                            "Xác nhận mật khẩu mới không được để trống"
                    );
                }

                if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
                    return ApiResponse.error(
                            "Cập nhật người dùng thất bại",
                            "Mật khẩu mới và xác nhận mật khẩu không khớp"
                    );
                }
            }

            // 4. Kiểm tra thay đổi FullName
            boolean fullNameChanged = request.getFullName() != null
                    && !request.getFullName().isBlank()
                    && !request.getFullName().equals(user.getFullName());

            // 5. Cập nhật thông tin cơ bản lên Keycloak (Username, Email, FullName)
            if (usernameChanged || emailChanged || fullNameChanged) {
                String username = usernameChanged ? request.getUsername() : user.getUsername();
                String email = emailChanged ? request.getEmail() : user.getEmail();
                String fullName = fullNameChanged ? request.getFullName() : user.getFullName();

                ApiResponse<Void> keycloakResult = keyCloakService.updateUser(
                        id,
                        username,
                        email,
                        fullName
                );

                if (!keycloakResult.isSuccess()) {
                    return keycloakResult;
                }
            }

            // 6. Cập nhật các trường thông tin trong Database local
            if (usernameChanged) {
                user.setUsername(request.getUsername());
            }

            if (emailChanged) {
                ApiResponse<Void> verifiedResult = keyCloakService.setEmailVerified(id, false);
                if (!verifiedResult.isSuccess()) {
                    return verifiedResult;
                }
                user.setEmailVerified(false);
                user.setEmail(request.getEmail());
            }

            if (fullNameChanged) {
                user.setFullName(request.getFullName());
            }

            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }

            // 7. Cập nhật Mật khẩu trên Keycloak
            if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
                ApiResponse<Void> passwordResult = keyCloakService.setPassword(
                        id,
                        request.getNewPassword(),
                        false
                );

                if (!passwordResult.isSuccess()) {
                    return passwordResult;
                }
            }

            // 8. Cập nhật Role trên Keycloak
            if (request.getRoleName() != null
                    && !request.getRoleName().isBlank()
                    && !request.getRoleName().equals(user.getRoleName())) {

                ApiResponse<Void> roleResult = keyCloakService.updateUserRole(id, request.getRoleName());
                if (!roleResult.isSuccess()) {
                    return roleResult;
                }
                user.setRoleName(request.getRoleName());
            }

            // 9. Cập nhật trạng thái Active trên Keycloak
            if (request.getIsActive() != null && !request.getIsActive().equals(user.getIsActive())) {
                ApiResponse<Void> enabledResult = keyCloakService.setUserEnabled(id, request.getIsActive());
                if (!enabledResult.isSuccess()) {
                    return enabledResult;
                }
                user.setIsActive(request.getIsActive());
            }

            userRepository.save(user);

            return ApiResponse.success("Cập nhật thông tin người dùng thành công");

        } catch (Exception ex) {
            log.error("Lỗi cập nhật người dùng {}: {}", id, ex.getMessage());
            return ApiResponse.error(
                    "Cập nhật thông tin thất bại",
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteUsers(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error(
                        "Xóa người dùng thất bại",
                        "Danh sách ID không được để trống"
                );
            }

            List<User> users = userRepository.findAllById(ids);
            if (users.isEmpty()) {
                return ApiResponse.error(
                        "Xóa người dùng thất bại",
                        "Không tìm thấy người dùng phù hợp"
                );
            }

            for (User user : users) {
                ApiResponse<Void> response = keyCloakService.deleteUser(user.getId());
                if (!response.isSuccess()) {
                    return ApiResponse.error(
                            "Xóa người dùng thất bại",
                            response.getErrorDetails()
                    );
                }
            }

            userRepository.deleteAll(users);

            return ApiResponse.success("Xóa thành công " + users.size() + " người dùng");

        } catch (Exception ex) {
            log.error("Lỗi xóa người dùng: {}", ex.getMessage());
            return ApiResponse.error(
                    "Xóa người dùng thất bại",
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> getUserById(String id) {
        return userRepository.findById(id)
                .map(user -> ApiResponse.success(
                        "Lấy thông tin người dùng thành công",
                        mapToResponse(user)
                ))
                .orElseGet(() -> ApiResponse.error(
                        "Không tìm thấy người dùng",
                        "ID không tồn tại: " + id
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String keyword, Pageable pageable) {
        String cleanKeyword = keyword != null ? keyword.trim() : null;

        String sortBy = "created_at";
        String direction = "DESC";

        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            sortBy = order.getProperty();
            direction = order.getDirection().name();
        }

        List<User> users = userMapper.searchUsers(
                cleanKeyword,
                pageable.getOffset(),
                pageable.getPageSize(),
                sortBy,
                direction
        );

        long total = userMapper.countSearchUsers(cleanKeyword);

        List<UserResponse> responses = users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, total);
    }

    @Override
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return keyCloakService.getAllRealmRoles();
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .roleName(user.getRoleName())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}