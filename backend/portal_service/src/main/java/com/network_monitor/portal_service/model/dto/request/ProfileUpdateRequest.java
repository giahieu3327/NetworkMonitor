package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.Size;
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

    private String oldPassword;

    @Size(
            min = 6,
            message = "Mật khẩu mới phải từ 6 ký tự trở lên"
    )
    private String newPassword;

    private String confirmNewPassword;
}