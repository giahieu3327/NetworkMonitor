package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;

public interface ProfileService {

    ApiResponse<UserResponse> getMyProfile(
            String currentUserId
    );

    ApiResponse<Void> updateMyProfile(
            String currentUserId,
            String currentUsername,
            ProfileUpdateRequest request
    );
}