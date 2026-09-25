package com.network_monitor.portal_service.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(min = 4, max = 50, message = "Username từ 4 đến 50 ký tự")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 6, message = "Password phải từ 6 ký tự trở lên")
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    private String phoneNumber;

    @NotBlank(message = "Role không được để trống")
    private String role;
}