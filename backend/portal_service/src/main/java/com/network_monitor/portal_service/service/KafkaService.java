package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.kafka.KafkaMessage;

public interface KafkaService {

    boolean sendToMonitor(
            String topic,
            String eventType,
            String key,
            Object data
    );

    void handleMonitorMessage(
            String topic,
            String key,
            KafkaMessage<?> message
    );
}