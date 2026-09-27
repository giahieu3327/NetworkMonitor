package com.network_monitor.portal_service.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {

    private String fullName;
    private String phoneNumber;
    private String oldPassword; // Bắt buộc nếu truyền newPassword
    private String newPassword;
}