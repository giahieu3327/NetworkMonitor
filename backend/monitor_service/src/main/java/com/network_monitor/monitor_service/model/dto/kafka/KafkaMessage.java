package com.network_monitor.monitor_service.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessage<T> {

    private String eventId;

    private String eventType;

    private String source;

    private LocalDateTime timestamp;

    private T data;
}