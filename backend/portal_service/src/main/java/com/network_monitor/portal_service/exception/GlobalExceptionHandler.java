package com.network_monitor.portal_service.exception;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1. Xử lý lỗi Validate dữ liệu DTO (@Valid / @NotBlank / @Email...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ApiResponse<Void> response = ApiResponse.error(
                "Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra lại!",
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 2. Xử lý lỗi Tham số không hợp lệ (IllegalArgumentException)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Tham số không hợp lệ",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. Xử lý lỗi Không đủ quyền hạn (AccessDeniedException - 403 Forbidden)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage());

        String details = (ex.getMessage() != null && !ex.getMessage().isBlank()) 
                ? ex.getMessage() : "Bạn không có quyền truy cập vào tài nguyên này!";

        ApiResponse<Void> response = ApiResponse.error("Từ chối truy cập", details);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // 4. Xử lý lỗi Xác thực sai thông tin đăng nhập (401 Unauthorized)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex) {
        ApiResponse<Void> response = ApiResponse.error(
                "Xác thực thất bại",
                "Tên đăng nhập hoặc mật khẩu không chính xác!"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // 5. Xử lý ngoại lệ Runtime chung trong quá trình xử lý logic
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException: ", ex);

        ApiResponse<Void> response = ApiResponse.error(
                "Xử lý yêu cầu thất bại",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 6. Xử lý tất cả các Exception không xác định khác (Lỗi hệ thống 500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Internal Server Error: ", ex);

        ApiResponse<Void> response = ApiResponse.error(
                "Lỗi hệ thống",
                "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau!"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}