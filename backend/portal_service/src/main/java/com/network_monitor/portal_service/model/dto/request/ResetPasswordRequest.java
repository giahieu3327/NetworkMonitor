package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Password mới không được để trống")
    @Size(min = 8, max = 100, message = "Password mới phải từ 8 đến 100 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận password không được để trống")
    @Size(min = 8, max = 100, message = "Xác nhận password phải từ 8 đến 100 ký tự")
    private String confirmPassword;
}