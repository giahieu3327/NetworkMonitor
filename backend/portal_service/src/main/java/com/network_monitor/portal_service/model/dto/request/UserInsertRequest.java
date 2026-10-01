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
public class UserInsertRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(
            min = 3,
            max = 50,
            message = "Tên đăng nhập từ 3 - 50 ký tự"
    )
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(
            min = 6,
            message = "Mật khẩu phải có ít nhất 6 ký tự"
    )
    private String password;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    private String confirmPassword;

    private String fullName;

    private String phoneNumber;

    private String roleName;

    private Boolean isActive;

    @Builder.Default
    private Boolean sendVerificationEmail = false;
}