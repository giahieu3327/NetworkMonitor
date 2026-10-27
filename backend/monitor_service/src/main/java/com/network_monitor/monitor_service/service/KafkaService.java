package com.network_monitor.monitor_service.service;

import com.network_monitor.monitor_service.model.dto.kafka.KafkaMessage;

import java.util.concurrent.CompletableFuture;

public interface KafkaService {

    CompletableFuture<Boolean> sendToPortal(
            String topic,
            String eventType,
            String key,
            Object data
    );

    void handlePortalMessage(
            String topic,
            String key,
            KafkaMessage<?> message
    );
}