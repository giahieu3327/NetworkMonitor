package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInterfaceResponse {

    private Long deviceId;

    private String deviceName;

    private Integer interfaceIndex;

    private String interfaceName;

    private Integer interfaceType;

    private Integer mtu;

    private Long speedBps;

    private String adminStatus;

    private String operStatus;

    private String physAddress;

    private String interfaceAlias;

    private String ipAddress;

    private String subnetMask;

    private OffsetDateTime updatedAt;
}