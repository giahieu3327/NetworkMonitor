package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.ProfileUpdateRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final KeyCloakService keyCloakService;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> getMyProfile(String currentUserId) {

        try {

            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Không tìm thấy thông tin tài khoản"
                            )
                    );

            return ApiResponse.success(
                    "Lấy thông tin cá nhân thành công",
                    mapToResponse(user)
            );

        } catch (Exception ex) {

            return ApiResponse.error(
                    "Lấy thông tin cá nhân thất bại",
                    ex.getMessage()
            );
        }
    }


    @Override
    @Transactional
    public ApiResponse<Void> updateMyProfile(
            String currentUserId,
            String currentUsername,
            ProfileUpdateRequest request
    ) {

        try {

            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Không tìm thấy thông tin tài khoản"
                            )
                    );


            // ========================================================
            // ĐỔI MẬT KHẨU
            // ========================================================

            if (request.getNewPassword() != null
                    && !request.getNewPassword().isBlank()) {

                // Kiểm tra mật khẩu cũ
                if (request.getOldPassword() == null
                        || request.getOldPassword().isBlank()) {

                    return ApiResponse.error(
                            "Đổi mật khẩu thất bại",
                            "Mật khẩu cũ không được để trống"
                    );
                }


                // Kiểm tra confirm password
                if (request.getConfirmNewPassword() == null
                        || request.getConfirmNewPassword().isBlank()) {

                    return ApiResponse.error(
                            "Đổi mật khẩu thất bại",
                            "Xác nhận mật khẩu mới không được để trống"
                    );
                }


                // Kiểm tra password == confirm password
                if (!request.getNewPassword()
                        .equals(request.getConfirmNewPassword())) {

                    return ApiResponse.error(
                            "Đổi mật khẩu thất bại",
                            "Mật khẩu mới và xác nhận mật khẩu không khớp"
                    );
                }


                // Gọi Keycloak đổi mật khẩu
                ApiResponse<Void> changePasswordResult =
                        keyCloakService.changePassword(
                                currentUserId,
                                currentUsername,
                                request.getOldPassword(),
                                request.getNewPassword()
                        );

                if (!changePasswordResult.isSuccess()) {
                    return changePasswordResult;
                }
            }


            // ========================================================
            // CẬP NHẬT FULL NAME
            // ========================================================

            if (request.getFullName() != null
                    && !request.getFullName().isBlank()) {

                ApiResponse<Void> keycloakResult =
                        keyCloakService.updateKeycloakUser(
                                currentUserId,
                                user.getEmail(),
                                request.getFullName()
                        );

                if (!keycloakResult.isSuccess()) {
                    return keycloakResult;
                }

                user.setFullName(request.getFullName());
            }


            // ========================================================
            // CẬP NHẬT PHONE
            // ========================================================

            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }


            // ========================================================
            // SAVE POSTGRES
            // ========================================================

            userRepository.save(user);

            return ApiResponse.success(
                    "Cập nhật thông tin cá nhân thành công"
            );

        } catch (Exception ex) {

            return ApiResponse.error(
                    "Cập nhật thông tin cá nhân thất bại",
                    ex.getMessage()
            );
        }
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