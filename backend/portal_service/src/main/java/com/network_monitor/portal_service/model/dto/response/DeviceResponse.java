package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {

    private Long id;
    private String deviceName;
    private String ipAddress;
    private String deviceType;
    private String model;
    private String firmwareVersion;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}