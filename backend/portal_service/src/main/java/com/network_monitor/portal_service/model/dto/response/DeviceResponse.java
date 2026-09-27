package com.network_monitor.portal_service.model.dto.response;

import com.network_monitor.portal_service.model.enums.DeviceStatus;
import com.network_monitor.portal_service.model.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {

    private Long id;
    private String deviceName;
    private String ipAddress;
    private DeviceType deviceType;
    private String model;
    private String firmwareVersion;
    private DeviceStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}