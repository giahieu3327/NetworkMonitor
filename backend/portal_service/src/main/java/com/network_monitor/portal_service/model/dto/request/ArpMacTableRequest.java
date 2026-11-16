package com.network_monitor.portal_service.model.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArpMacTableRequest {

    // ID thiết bị ghi nhận ARP.
    private Long deviceId;

    // Interface index, có thể NULL.
    private Integer interfaceIndex;

    // Địa chỉ IP.
    private String ipAddress;

    // Địa chỉ MAC.
    private String macAddress;
}