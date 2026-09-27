package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.ProfileUpdateRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import com.network_monitor.portal_service.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // 1. Lấy Profile cá nhân của người dùng đang đăng nhập
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String currentUserId = jwt.getSubject();
        ApiResponse<UserResponse> response = profileService.getMyProfile(currentUserId);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 2. Cập nhật Profile cá nhân (Đổi mật khẩu, Họ tên, SĐT)
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileUpdateRequest request) {
        String currentUserId = jwt.getSubject();
        String currentUsername = jwt.getClaimAsString("preferred_username");
        ApiResponse<Void> response = profileService.updateMyProfile(currentUserId, currentUsername, request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}