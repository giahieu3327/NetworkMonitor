package com.network_monitor.portal_service.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Bỏ qua các trường null khi serialize ra JSON
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private Map<String, String> details; // Chứa lỗi validate chi tiết theo từng field (nếu có)
}