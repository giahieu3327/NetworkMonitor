
package com.network_monitor.monitor_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.monitor_service.model.dto.kafka.*;
import com.network_monitor.monitor_service.model.dto.kafka.device.*;
import com.network_monitor.monitor_service.model.dto.kafka.credential.*;
import com.network_monitor.monitor_service.model.dto.kafka.oidconfig.*;
import com.network_monitor.monitor_service.model.dto.kafka.metric.*;
import com.network_monitor.monitor_service.model.dto.kafka.syslog.*;
import com.network_monitor.monitor_service.service.DeviceCredentialService;
import com.network_monitor.monitor_service.service.DeviceService;
import com.network_monitor.monitor_service.service.KafkaService;
import com.network_monitor.monitor_service.service.OidConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaServiceImpl
        implements KafkaService, ConsumerSeekAware {

    private static final String MONITOR = "monitor_service";
    private static final String PORTAL = "portal_service";

    private static final String DEVICE =
            "portal.monitor.device";

    private static final String OID_CONFIG =
            "portal.monitor.oidconfig";

    private static final String CREDENTIAL =
            "portal.monitor.device-credential";

    // Tạm thời bỏ qua device-interface.
    private static final String METRIC =
            "monitor.portal.metric";

    private static final String SYSLOG =
            "monitor.portal.syslog";

    private static final Set<String> REQUIRED_SYNCS = Set.of(
            DEVICE,
            OID_CONFIG,
            CREDENTIAL
    );

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final ObjectProvider<DeviceService> deviceService;
    private final ObjectProvider<DeviceCredentialService> credentialService;
    private final ObjectProvider<OidConfigService> oidConfigService;

    /**
     * Chỉ READY sau khi đã áp dụng thành công đủ ba snapshot.
     */
    private final AtomicBoolean ready =
            new AtomicBoolean(false);

    /**
     * Tránh gửi lặp ba yêu cầu đồng bộ trong cùng vòng đời instance.
     */
    private final AtomicBoolean syncRequestsSent =
            new AtomicBoolean(false);

    /**
     * Các topic đã áp dụng SYNC_RESPONSE thành công.
     */
    private final Set<String> completedSyncs =
            ConcurrentHashMap.newKeySet();

    // =========================================================
    // STARTUP SYNC / SKIP OLD MESSAGES
    // =========================================================

    @Override
    public void registerSeekCallback(
            ConsumerSeekAware.ConsumerSeekCallback callback
    ) {
        // Không cần lưu callback.
    }

    /**
     * Khi consumer được gán partition:
     * 1. Đưa vị trí đọc đến cuối các partition.
     * 2. Bỏ qua những message đã tồn tại trước thời điểm này.
     * 3. Gửi yêu cầu đồng bộ snapshot từ Portal.
     */
    @Override
    public void onPartitionsAssigned(
            Map<TopicPartition, Long> assignments,
            ConsumerSeekAware.ConsumerSeekCallback callback
    ) {
        Set<String> assignedTopics =
                ConcurrentHashMap.newKeySet();

        for (TopicPartition partition : assignments.keySet()) {
            assignedTopics.add(partition.topic());
        }

        if (!assignedTopics.containsAll(REQUIRED_SYNCS)) {
            log.info(
                    "Waiting for all required topics to be assigned. " +
                    "Assigned topics={}",
                    assignedTopics
            );
            return;
        }

        ready.set(false);
        completedSyncs.clear();

        for (TopicPartition partition : assignments.keySet()) {
            callback.seekToEnd(
                    partition.topic(),
                    partition.partition()
            );

            log.info(
                    "Seeking to end: topic={}, partition={}",
                    partition.topic(),
                    partition.partition()
            );
        }

        if (syncRequestsSent.compareAndSet(false, true)) {
            log.info(
                    "Old messages will be skipped. " +
                    "Requesting configuration snapshots from Portal."
            );

            requestSync(DEVICE);
            requestSync(OID_CONFIG);
            requestSync(CREDENTIAL);
        }
    }

    @Override
    public void onPartitionsRevoked(
            Collection<TopicPartition> partitions
    ) {
        // Không xử lý CRUD tại đây.
    }

    private void requestSync(String topic) {
        sendToPortal(
                topic,
                "SYNC_REQUEST",
                "latest",
                null
        ).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                log.info(
                        "Sync request sent: topic={}",
                        topic
                );
            } else {
                log.error(
                        "Sync request failed: topic={}",
                        topic
                );
            }
        });
    }

    public boolean isReady() {
        return ready.get();
    }

    private void markSyncCompleted(String topic) {
        completedSyncs.add(topic);

        log.info(
                "Sync applied: topic={}, completed={}/{}",
                topic,
                completedSyncs.size(),
                REQUIRED_SYNCS.size()
        );

        if (completedSyncs.containsAll(REQUIRED_SYNCS)) {
            ready.set(true);

            log.info(
                    "All required configuration syncs applied. " +
                    "Monitor is READY."
            );
        }
    }

    // =========================================================
    // SEND: MONITOR -> PORTAL
    // =========================================================

    @Override
    public CompletableFuture<Boolean> sendToPortal(
            String topic,
            String eventType,
            String key,
            Object data
    ) {
        final Object dto;

        try {
            dto = convertOutgoingData(topic, eventType, data);
        } catch (RuntimeException e) {
            log.error(
                    "Kafka data conversion failed: topic={}, " +
                    "eventType={}, key={}",
                    topic, eventType, key, e
            );

            return CompletableFuture.completedFuture(false);
        }

        String eventId = UUID.randomUUID().toString();

        KafkaMessage<Object> message = KafkaMessage.builder()
                .eventId(eventId)
                .eventType(eventType)
                .source(MONITOR)
                .timestamp(LocalDateTime.now())
                .data(dto)
                .build();

        log.info(
                "Kafka send started: eventId={}, topic={}, " +
                "eventType={}, key={}",
                eventId, topic, eventType, key
        );

        return kafkaTemplate.send(topic, key, message)
                .thenApply(result -> {
                    log.info(
                            "Kafka send succeeded: eventId={}, " +
                            "topic={}, eventType={}",
                            eventId, topic, eventType
                    );
                    return true;
                })
                .exceptionally(e -> {
                    log.error(
                            "Kafka send failed: eventId={}, " +
                            "topic={}, eventType={}",
                            eventId, topic, eventType, e
                    );
                    return false;
                });
    }

    private Object convertOutgoingData(
            String topic,
            String eventType,
            Object data
    ) {
        return switch (topic) {
            case DEVICE -> switch (eventType) {
                case "UPDATE_STATUS" ->
                        convertData(
                                data,
                                DeviceStatusEventDto.class
                        );

                case "SYNC_REQUEST" ->
                        convertDataOrEmpty(
                                data,
                                DeviceSyncRequestDto.class,
                                DeviceSyncRequestDto::new
                        );

                default ->
                        throw unsupportedEvent(topic, eventType);
            };

            case OID_CONFIG -> switch (eventType) {
                case "SYNC_REQUEST" ->
                        convertDataOrEmpty(
                                data,
                                OidConfigSyncRequestDto.class,
                                OidConfigSyncRequestDto::new
                        );

                default ->
                        throw unsupportedEvent(topic, eventType);
            };

            case CREDENTIAL -> switch (eventType) {
                case "SYNC_REQUEST" ->
                        convertDataOrEmpty(
                                data,
                                DeviceCredentialSyncRequestDto.class,
                                DeviceCredentialSyncRequestDto::new
                        );

                default ->
                        throw unsupportedEvent(topic, eventType);
            };

            case METRIC -> {
                requireEvent(topic, eventType, "COLLECT");
                yield convertData(data, MetricCollectDto.class);
            }

            case SYSLOG -> {
                requireEvent(topic, eventType, "COLLECT");
                yield convertData(data, SyslogCollectDto.class);
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported outgoing Kafka topic: " + topic
            );
        };
    }

    // =========================================================
    // RECEIVE: PORTAL -> MONITOR
    // =========================================================

    @Override
    @KafkaListener(
            topics = {
                    DEVICE,
                    OID_CONFIG,
                    CREDENTIAL
            },
            groupId = "${spring.kafka.consumer.group-id:monitor-group}"
    )
    public void handlePortalMessage(
            @Header(KafkaHeaders.RECEIVED_TOPIC)
            String topic,

            @Header(
                    name = KafkaHeaders.RECEIVED_KEY,
                    required = false
            )
            String key,

            KafkaMessage<?> message
    ) {
        if (message == null) {
            log.warn(
                    "Kafka message ignored: topic={}, key={}, " +
                    "reason=null message",
                    topic, key
            );
            return;
        }

        String eventId = message.getEventId();
        String eventType = message.getEventType();
        String source = message.getSource();

        log.info(
                "Kafka message received: eventId={}, topic={}, " +
                "eventType={}, key={}, source={}",
                eventId, topic, eventType, key, source
        );

        // Chỉ nhận message do Portal gửi.
        if (!PORTAL.equals(source)) {
            log.warn(
                    "Kafka message ignored: eventId={}, " +
                    "topic={}, source={}",
                    eventId, topic, source
            );
            return;
        }

        /*
         * Chưa READY:
         * - Không xử lý CREATE, UPDATE, DELETE.
         * - Chỉ xử lý SYNC_RESPONSE.
         */
        if (!ready.get() && !"SYNC_RESPONSE".equals(eventType)) {
            log.warn(
                    "Kafka event ignored because Monitor is not ready: " +
                    "eventId={}, topic={}, eventType={}, key={}",
                    eventId, topic, eventType, key
            );
            return;
        }

        try {
            switch (topic) {
                case DEVICE ->
                        handleDeviceMessage(
                                eventType,
                                message.getData(),
                                topic,
                                key
                        );

                case OID_CONFIG ->
                        handleOidConfigMessage(
                                eventType,
                                message.getData(),
                                topic,
                                key
                        );

                case CREDENTIAL ->
                        handleCredentialMessage(
                                eventType,
                                message.getData(),
                                topic,
                                key
                        );

                default -> log.warn(
                        "Unsupported incoming topic: {}",
                        topic
                );
            }
        } catch (RuntimeException e) {
            log.error(
                    "Kafka message processing failed: eventId={}, " +
                    "topic={}, eventType={}, key={}",
                    eventId, topic, eventType, key, e
            );

            throw e;
        }
    }

    // =========================================================
    // DEVICE EVENTS
    // =========================================================

    private void handleDeviceMessage(
            String eventType,
            Object data,
            String topic,
            String key
    ) {
        switch (eventType) {
            case "CREATE" -> {
                DeviceEventDto dto =
                        convertData(data, DeviceEventDto.class);

                deviceService.getObject().createDevice(dto);
            }

            case "UPDATE" -> {
                DeviceEventDto dto =
                        convertData(data, DeviceEventDto.class);

                deviceService.getObject().updateDevice(dto);
            }

            case "DELETE" -> {
                DeviceDeleteEventDto dto =
                        convertData(
                                data,
                                DeviceDeleteEventDto.class
                        );

                deviceService.getObject().deleteDevice(dto);
            }

            case "SYNC_RESPONSE" -> {
                DeviceSyncResponseDto dto =
                        convertData(
                                data,
                                DeviceSyncResponseDto.class
                        );

                applyDeviceSyncResponse(dto);
            }

            default -> log.warn(
                    "Unsupported device event: topic={}, " +
                    "eventType={}, key={}",
                    topic, eventType, key
            );
        }
    }

    // =========================================================
    // OID CONFIG EVENTS
    // =========================================================

    private void handleOidConfigMessage(
            String eventType,
            Object data,
            String topic,
            String key
    ) {
        switch (eventType) {
            case "CREATE" -> {
                OidConfigEventDto dto =
                        convertData(
                                data,
                                OidConfigEventDto.class
                        );

                oidConfigService.getObject().createOidConfig(dto);
            }

            case "UPDATE" -> {
                OidConfigEventDto dto =
                        convertData(
                                data,
                                OidConfigEventDto.class
                        );

                oidConfigService.getObject().updateOidConfig(dto);
            }

            case "DELETE" -> {
                OidConfigDeleteEventDto dto =
                        convertData(
                                data,
                                OidConfigDeleteEventDto.class
                        );

                oidConfigService.getObject().deleteOidConfig(dto);
            }

            case "SYNC_RESPONSE" -> {
                OidConfigSyncResponseDto dto =
                        convertData(
                                data,
                                OidConfigSyncResponseDto.class
                        );

                applyOidConfigSyncResponse(dto);
            }

            default -> log.warn(
                    "Unsupported OID config event: topic={}, " +
                    "eventType={}, key={}",
                    topic, eventType, key
            );
        }
    }

    // =========================================================
    // CREDENTIAL EVENTS
    // =========================================================

    private void handleCredentialMessage(
            String eventType,
            Object data,
            String topic,
            String key
    ) {
        switch (eventType) {
            case "CREATE" -> {
                DeviceCredentialEventDto dto =
                        convertData(
                                data,
                                DeviceCredentialEventDto.class
                        );

                credentialService.getObject()
                        .createCredential(dto);
            }

            case "UPDATE" -> {
                DeviceCredentialEventDto dto =
                        convertData(
                                data,
                                DeviceCredentialEventDto.class
                        );

                credentialService.getObject()
                        .updateCredential(dto);
            }

            case "DELETE" -> {
                DeviceCredentialDeleteEventDto dto =
                        convertData(
                                data,
                                DeviceCredentialDeleteEventDto.class
                        );

                credentialService.getObject()
                        .deleteCredential(dto);
            }

            case "SYNC_RESPONSE" -> {
                DeviceCredentialSyncResponseDto dto =
                        convertData(
                                data,
                                DeviceCredentialSyncResponseDto.class
                        );

                applyCredentialSyncResponse(dto);
            }

            default -> log.warn(
                    "Unsupported credential event: topic={}, " +
                    "eventType={}, key={}",
                    topic, eventType, key
            );
        }
    }

    // =========================================================
    // APPLY SYNC RESPONSES
    // =========================================================

    private void applyDeviceSyncResponse(
            DeviceSyncResponseDto dto
    ) {
        if (dto.getDevices() == null) {
            throw new IllegalArgumentException(
                    "Device sync response does not contain devices"
            );
        }

        for (DeviceEventDto device : dto.getDevices()) {
            deviceService.getObject().createDevice(device);
        }

        log.info(
                "Device sync response applied: count={}",
                dto.getDevices().size()
        );

        markSyncCompleted(DEVICE);
    }

    private void applyOidConfigSyncResponse(
            OidConfigSyncResponseDto dto
    ) {
        if (dto.getOidConfigs() == null) {
            throw new IllegalArgumentException(
                    "OID config sync response does not contain oidConfigs"
            );
        }

        for (OidConfigEventDto config : dto.getOidConfigs()) {
            oidConfigService.getObject().createOidConfig(config);
        }

        log.info(
                "OID config sync response applied: count={}",
                dto.getOidConfigs().size()
        );

        markSyncCompleted(OID_CONFIG);
    }

    private void applyCredentialSyncResponse(
            DeviceCredentialSyncResponseDto dto
    ) {
        if (dto.getCredentials() == null) {
            throw new IllegalArgumentException(
                    "Credential sync response does not contain credentials"
            );
        }

        for (DeviceCredentialEventDto credential
                : dto.getCredentials()) {
            credentialService.getObject()
                    .createCredential(credential);
        }

        log.info(
                "Credential sync response applied: count={}",
                dto.getCredentials().size()
        );

        markSyncCompleted(CREDENTIAL);
    }

    // =========================================================
    // DTO CONVERSION HELPERS
    // =========================================================

    private <T> T convertData(
            Object data,
            Class<T> dtoClass
    ) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Kafka data must not be null for DTO "
                            + dtoClass.getSimpleName()
            );
        }

        return objectMapper.convertValue(data, dtoClass);
    }

    private <T> T convertDataOrEmpty(
            Object data,
            Class<T> dtoClass,
            java.util.function.Supplier<T> emptySupplier
    ) {
        if (data == null) {
            return emptySupplier.get();
        }

        return objectMapper.convertValue(data, dtoClass);
    }

    private void requireEvent(
            String topic,
            String actualEvent,
            String expectedEvent
    ) {
        if (!expectedEvent.equals(actualEvent)) {
            throw unsupportedEvent(topic, actualEvent);
        }
    }

    private IllegalArgumentException unsupportedEvent(
            String topic,
            String eventType
    ) {
        return new IllegalArgumentException(
                "Unsupported eventType '" + eventType
                        + "' for topic '" + topic + "'"
        );
    }
}
