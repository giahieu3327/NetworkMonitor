package com.network_monitor.portal_service.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInterfaceRequest {

    private Long deviceId;

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
}