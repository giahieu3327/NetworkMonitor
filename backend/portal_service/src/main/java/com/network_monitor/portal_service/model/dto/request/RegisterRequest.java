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
public class RegisterRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(max = 50, message = "Username không được vượt quá 50 ký tự")
    private String username;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 8, max = 100, message = "Password phải từ 8 đến 100 ký tự")
    private String password;

    @NotBlank(message = "Xác nhận password không được để trống")
    @Size(min = 8, max = 100, message = "Xác nhận password phải từ 8 đến 100 ký tự")
    private String confirmPassword;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;

    @NotBlank(message = "Role không được để trống")
    @Size(max = 50, message = "Role không được vượt quá 50 ký tự")
    private String roleName;

    @Builder.Default
    private Boolean sendEmailVerify = true;
}