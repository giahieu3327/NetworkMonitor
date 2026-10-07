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

    @Size(max = 50, message = "Username không được vượt quá 50 ký tự")
    private String username;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;

    @Size(max = 50, message = "Role không được vượt quá 50 ký tự")
    private String roleName;

    private Boolean isActive;

    @Size(min = 8, max = 100, message = "Password mới phải từ 8 đến 100 ký tự")
    private String newPassword;

    @Size(min = 8, max = 100, message = "Xác nhận password mới phải từ 8 đến 100 ký tự")
    private String confirmNewPassword;
}