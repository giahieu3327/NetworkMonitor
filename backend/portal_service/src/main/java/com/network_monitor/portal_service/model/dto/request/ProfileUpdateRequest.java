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

    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;

    private String oldPassword;

    @Size(min = 8, max = 100, message = "Password mới phải từ 8 đến 100 ký tự")
    private String newPassword;

    @Size(min = 8, max = 100, message = "Xác nhận password mới phải từ 8 đến 100 ký tự")
    private String confirmNewPassword;
}