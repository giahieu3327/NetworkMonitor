package com.network_monitor.monitor_service.model.dto.kafka.syslog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyslogCollectDto {

    private Long deviceId;
    private String sourceIp;
    private String severity;
    private String facility;
    private String message;
    private LocalDateTime receivedAt;
}