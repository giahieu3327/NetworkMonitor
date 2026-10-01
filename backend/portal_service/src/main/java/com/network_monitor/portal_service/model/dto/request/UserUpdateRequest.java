package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String username;

    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @Size(
            min = 6,
            message = "Mật khẩu mới phải từ 6 ký tự trở lên"
    )
    private String newPassword;

    private String confirmNewPassword;

    private String fullName;

    private String phoneNumber;

    private String roleName;

    private Boolean isActive;
}