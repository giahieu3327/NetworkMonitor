package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.ProfileUpdateRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;

public interface ProfileService {
    ApiResponse<UserResponse> getMyProfile(String currentUserId);
    ApiResponse<Void> updateMyProfile(String currentUserId, String currentUsername, ProfileUpdateRequest request);
}