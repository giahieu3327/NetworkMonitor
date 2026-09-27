package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.LoginRequest;
import com.network_monitor.portal_service.model.dto.request.LogoutRequest;
import com.network_monitor.portal_service.model.dto.request.RefreshTokenRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;

public interface AuthService {
    ApiResponse<TokenResponse> login(LoginRequest request);
    ApiResponse<TokenResponse> refreshToken(RefreshTokenRequest request);
    ApiResponse<Void> logout(LogoutRequest request);
}