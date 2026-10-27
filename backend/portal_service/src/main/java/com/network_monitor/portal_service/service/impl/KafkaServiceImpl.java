package com.network_monitor.portal_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.model.dto.kafka.*;
import com.network_monitor.portal_service.model.dto.kafka.device.*;
import com.network_monitor.portal_service.model.dto.kafka.credential.*;
import com.network_monitor.portal_service.model.dto.kafka.oidconfig.*;
import com.network_monitor.portal_service.model.dto.kafka.deviceinterface.*;
import com.network_monitor.portal_service.model.dto.kafka.metric.*;
import com.network_monitor.portal_service.model.dto.kafka.syslog.*;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.service.KafkaService;
import com.network_monitor.portal_service.service.DeviceService;
import com.network_monitor.portal_service.service.DeviceCredentialService;
import com.network_monitor.portal_service.service.OidConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaServiceImpl implements KafkaService {

    private static final String PORTAL = "portal_service";
    private static final String MONITOR = "monitor_service";

    private static final String DEVICE = "portal.monitor.device";
    private static final String OID_CONFIG = "portal.monitor.oidconfig";
    private static final String CREDENTIAL =
            "portal.monitor.device-credential";
    private static final String DEVICE_INTERFACE =
            "monitor.portal.device-interface";
    private static final String METRIC = "monitor.portal.metric";
    private static final String SYSLOG = "monitor.portal.syslog";

    private static final int PAGE_SIZE = 100;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final ObjectProvider<DeviceService> deviceService;
    private final ObjectProvider<DeviceCredentialService> credentialService;
    private final ObjectProvider<OidConfigService> oidConfigService;

    // =========================================================
    // SEND: PORTAL -> MONITOR
    // =========================================================

    @Override
    public boolean sendToMonitor(
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
                    "Kafka data conversion failed: topic={}, eventType={}, key={}",
                    topic, eventType, key, e
            );
            return false;
        }

        String eventId = UUID.randomUUID().toString();

        KafkaMessage<Object> message = KafkaMessage.builder()
                .eventId(eventId)
                .eventType(eventType)
                .source(PORTAL)
                .timestamp(LocalDateTime.now())
                .data(dto)
                .build();

        log.info(
                "Kafka send started: eventId={}, topic={}, eventType={}, key={}, dataType={}",
                eventId,
                topic,
                eventType,
                key,
                dto == null ? "null" : dto.getClass().getSimpleName()
        );

        try {
            kafkaTemplate.send(topic, key, message).get();

            log.info(
                    "Kafka send succeeded: eventId={}, topic={}, eventType={}, key={}",
                    eventId, topic, eventType, key
            );

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.error(
                    "Kafka send interrupted: eventId={}, topic={}, eventType={}, key={}",
                    eventId, topic, eventType, key, e
            );

            return false;

        } catch (ExecutionException | RuntimeException e) {
            log.error(
                    "Kafka send failed: eventId={}, topic={}, eventType={}, key={}",
                    eventId, topic, eventType, key, e
            );

            return false;
        }
    }

    private Object convertOutgoingData(
            String topic,
            String eventType,
            Object data
    ) {
        return switch (topic) {
            case DEVICE -> switch (eventType) {
                case "CREATE", "UPDATE" ->
                        convertData(data, DeviceEventDto.class);

                case "DELETE" ->
                        convertData(data, DeviceDeleteEventDto.class);

                case "SYNC_RESPONSE" ->
                        convertData(data, DeviceSyncResponseDto.class);

                default -> throw unsupportedEvent(topic, eventType);
            };

            case OID_CONFIG -> switch (eventType) {
                case "CREATE", "UPDATE" ->
                        convertData(data, OidConfigEventDto.class);

                case "DELETE" ->
                        convertData(data, OidConfigDeleteEventDto.class);

                case "SYNC_RESPONSE" ->
                        convertData(data, OidConfigSyncResponseDto.class);

                default -> throw unsupportedEvent(topic, eventType);
            };

            case CREDENTIAL -> switch (eventType) {
                case "CREATE", "UPDATE" ->
                        convertData(data, DeviceCredentialEventDto.class);

                case "DELETE" ->
                        convertData(data, DeviceCredentialDeleteEventDto.class);

                case "SYNC_RESPONSE" ->
                        convertData(data, DeviceCredentialSyncResponseDto.class);

                default -> throw unsupportedEvent(topic, eventType);
            };

            case DEVICE_INTERFACE -> {
                requireEvent(topic, eventType, "SYNC_RESPONSE");
                yield convertData(data, DeviceInterfaceSyncResponseDto.class);
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported outgoing Kafka topic: " + topic
            );
        };
    }

    // =========================================================
    // RECEIVE: MONITOR -> PORTAL
    // =========================================================

    @Override
    @KafkaListener(
            topics = {
                    DEVICE,
                    OID_CONFIG,
                    CREDENTIAL,
                    DEVICE_INTERFACE,
                    METRIC,
                    SYSLOG
            },
            groupId = "${spring.kafka.consumer.group-id:portal-group}"
    )
    public void handleMonitorMessage(
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(
                    name = KafkaHeaders.RECEIVED_KEY,
                    required = false
            ) String key,
            KafkaMessage<?> message
    ) {
        if (message == null) {
            log.warn(
                    "Kafka message ignored: topic={}, key={}, reason=message is null",
                    topic, key
            );
            return;
        }

        String eventId = message.getEventId();
        String eventType = message.getEventType();
        String source = message.getSource();

        log.info(
                "Kafka message received: eventId={}, topic={}, eventType={}, key={}, source={}",
                eventId, topic, eventType, key, source
        );

        // Portal chỉ xử lý message do Monitor gửi.
        if (!MONITOR.equals(source)) {
            log.debug(
                    "Kafka message ignored: eventId={}, topic={}, source={}",
                    eventId, topic, source
            );
            return;
        }

        try {
            switch (topic) {
                case DEVICE ->
                        handleDeviceMessage(
                                eventType, message.getData(), topic, key
                        );

                case OID_CONFIG, CREDENTIAL ->
                        handleConfigMessage(
                                eventType, message.getData(), topic, key
                        );

                // Tạm thời chưa đồng bộ device interface.
                case DEVICE_INTERFACE ->
                        log.debug(
                                "Device interface topic temporarily ignored: eventType={}, key={}",
                                eventType, key
                        );

                case METRIC -> {
                    if ("COLLECT".equals(eventType)) {
                        MetricCollectDto dto = convertData(
                                message.getData(), MetricCollectDto.class
                        );

                        log.info(
                                "Metric received: deviceId={}, key={}",
                                dto.getDeviceId(), key
                        );
                    } else {
                        log.warn(
                                "Unsupported metric event: eventId={}, eventType={}",
                                eventId, eventType
                        );
                    }
                }

                case SYSLOG -> {
                    if ("COLLECT".equals(eventType)) {
                        SyslogCollectDto dto = convertData(
                                message.getData(), SyslogCollectDto.class
                        );

                        log.info(
                                "Syslog received: deviceId={}, key={}",
                                dto.getDeviceId(), key
                        );
                    } else {
                        log.warn(
                                "Unsupported syslog event: eventId={}, eventType={}",
                                eventId, eventType
                        );
                    }
                }

                default -> log.warn(
                        "Unknown Kafka topic: eventId={}, topic={}",
                        eventId, topic
                );
            }

        } catch (RuntimeException e) {
            log.error(
                    "Kafka message processing failed: eventId={}, topic={}, eventType={}, key={}",
                    eventId, topic, eventType, key, e
            );

            throw e;
        }
    }

    private void handleDeviceMessage(
            String eventType,
            Object data,
            String topic,
            String key
    ) {
        switch (eventType) {
            case "UPDATE_STATUS" -> {
                DeviceStatusEventDto dto = convertData(
                        data, DeviceStatusEventDto.class
                );

                log.info(
                        "Device status update received: deviceId={}, status={}, key={}",
                        dto.getDeviceId(), dto.getStatus(), key
                );

                deviceService.getObject()
                        .updateStatus(dto.getDeviceId(), dto.getStatus());
            }

            case "SYNC_REQUEST" ->
                    handleSyncRequest(topic, key);

            default -> log.warn(
                    "Unsupported device event: topic={}, eventType={}, key={}",
                    topic, eventType, key
            );
        }
    }

    private void handleConfigMessage(
            String eventType,
            Object data,
            String topic,
            String key
    ) {
        if (!"SYNC_REQUEST".equals(eventType)) {
            log.warn(
                    "Unsupported config event: topic={}, eventType={}, key={}",
                    topic, eventType, key
            );
            return;
        }

        // Kiểm tra payload theo đúng topic.
        if (OID_CONFIG.equals(topic)) {
            convertDataOrEmpty(
                    data,
                    OidConfigSyncRequestDto.class,
                    OidConfigSyncRequestDto::new
            );
        } else if (CREDENTIAL.equals(topic)) {
            convertDataOrEmpty(
                    data,
                    DeviceCredentialSyncRequestDto.class,
                    DeviceCredentialSyncRequestDto::new
            );
        }

        handleSyncRequest(topic, key);
    }

    // =========================================================
    // SYNC REQUEST HANDLING
    // =========================================================

    private void handleSyncRequest(String topic, String key) {
        log.info(
                "Sync request received: topic={}, key={}",
                topic, key
        );

        switch (topic) {
            case DEVICE -> syncDevices(key);
            case OID_CONFIG -> syncOidConfigs(key);
            case CREDENTIAL -> syncCredentials(key);

            default -> log.warn(
                    "Sync request ignored for unsupported topic: topic={}, key={}",
                    topic, key
            );
        }
    }

    // =========================================================
    // DEVICE SYNC
    // SYNC_REQUEST on DEVICE
    // -> Query all devices
    // -> SYNC_RESPONSE on DEVICE
    // =========================================================

    private void syncDevices(String key) {
        log.info("Starting device sync: key={}", key);

        try {
            List<DeviceResponse> deviceResponses = getAllDevices();

            List<DeviceEventDto> devices = deviceResponses.stream()
                    .map(device ->
                            objectMapper.convertValue(
                                    device, DeviceEventDto.class
                            )
                    )
                    .toList();

            DeviceSyncResponseDto response = DeviceSyncResponseDto.builder()
                    .devices(devices)
                    .build();

            boolean sent = sendToMonitor(
                    DEVICE,
                    "SYNC_RESPONSE",
                    key,
                    response
            );

            if (sent) {
                log.info(
                        "Device sync response sent: count={}, key={}",
                        devices.size(), key
                );
            } else {
                log.error("Failed to send device sync response: key={}", key);
            }

        } catch (RuntimeException e) {
            log.error("Device sync failed: key={}", key, e);
        }
    }

    // =========================================================
    // CREDENTIAL SYNC
    // SYNC_REQUEST on CREDENTIAL
    // -> Query all devices
    // -> Query credentials for each deviceId
    // -> SYNC_RESPONSE on CREDENTIAL
    // =========================================================

    private void syncCredentials(String key) {
        log.info("Starting credential sync: key={}", key);

        try {
            List<DeviceResponse> devices = getAllDevices();
            List<DeviceCredentialEventDto> allCredentials =
                    new ArrayList<>();

            for (DeviceResponse device : devices) {
                Long deviceId = device.getId();

                if (deviceId == null) {
                    log.warn(
                            "Skipping credential sync for device with null ID"
                    );
                    continue;
                }

                List<DeviceCredentialResponse> credentials =
                        getAllCredentialsByDeviceId(deviceId);

                for (DeviceCredentialResponse credential : credentials) {
                    DeviceCredentialEventDto dto =
                            objectMapper.convertValue(
                                    credential,
                                    DeviceCredentialEventDto.class
                            );

                    // Đảm bảo deviceId đúng với thiết bị đang truy vấn.
                    dto.setDeviceId(deviceId);
                    allCredentials.add(dto);
                }
            }

            DeviceCredentialSyncResponseDto response =
                    DeviceCredentialSyncResponseDto.builder()
                            .credentials(allCredentials)
                            .build();

            boolean sent = sendToMonitor(
                    CREDENTIAL,
                    "SYNC_RESPONSE",
                    key,
                    response
            );

            if (sent) {
                log.info(
                        "Credential sync response sent: deviceCount={}, credentialCount={}, key={}",
                        devices.size(), allCredentials.size(), key
                );
            } else {
                log.error(
                        "Failed to send credential sync response: key={}",
                        key
                );
            }

        } catch (RuntimeException e) {
            log.error("Credential sync failed: key={}", key, e);
        }
    }

    // =========================================================
    // OID CONFIG SYNC
    // SYNC_REQUEST on OID_CONFIG
    // -> Query all devices
    // -> Query OID configs for each deviceId
    // -> SYNC_RESPONSE on OID_CONFIG
    // =========================================================

    private void syncOidConfigs(String key) {
        log.info("Starting OID config sync: key={}", key);

        try {
            List<DeviceResponse> devices = getAllDevices();
            List<OidConfigEventDto> allOidConfigs =
                    new ArrayList<>();

            for (DeviceResponse device : devices) {
                Long deviceId = device.getId();

                if (deviceId == null) {
                    log.warn(
                            "Skipping OID config sync for device with null ID"
                    );
                    continue;
                }

                List<OidConfigResponse> configs =
                        getAllOidConfigsByDeviceId(deviceId);

                for (OidConfigResponse config : configs) {
                    OidConfigEventDto dto =
                            objectMapper.convertValue(
                                    config,
                                    OidConfigEventDto.class
                            );

                    // Đảm bảo deviceId đúng với thiết bị đang truy vấn.
                    dto.setDeviceId(deviceId);
                    allOidConfigs.add(dto);
                }
            }

            OidConfigSyncResponseDto response =
                    OidConfigSyncResponseDto.builder()
                            .oidConfigs(allOidConfigs)
                            .build();

            boolean sent = sendToMonitor(
                    OID_CONFIG,
                    "SYNC_RESPONSE",
                    key,
                    response
            );

            if (sent) {
                log.info(
                        "OID config sync response sent: deviceCount={}, oidConfigCount={}, key={}",
                        devices.size(), allOidConfigs.size(), key
                );
            } else {
                log.error(
                        "Failed to send OID config sync response: key={}",
                        key
                );
            }

        } catch (RuntimeException e) {
            log.error("OID config sync failed: key={}", key, e);
        }
    }

    // =========================================================
    // QUERY ALL PAGES
    // =========================================================

    private List<DeviceResponse> getAllDevices() {
        return getAllPages(pageRequest ->
                deviceService.getObject().getDevices(pageRequest)
        );
    }

    private List<DeviceCredentialResponse> getAllCredentialsByDeviceId(
            Long deviceId
    ) {
        return getAllPages(pageRequest ->
                credentialService.getObject()
                        .getCredentialsByDeviceId(deviceId, pageRequest)
        );
    }

    private List<OidConfigResponse> getAllOidConfigsByDeviceId(
            Long deviceId
    ) {
        return getAllPages(pageRequest ->
                oidConfigService.getObject()
                        .getOidConfigsByDeviceId(deviceId, pageRequest)
        );
    }

    /**
     * Duyệt hết tất cả các trang, không chỉ lấy trang đầu tiên.
     */
    private <T> List<T> getAllPages(
            Function<SearchRequest, Page<T>> query
    ) {
        List<T> results = new ArrayList<>();
        int pageNumber = 0;

        while (true) {
            SearchRequest request = SearchRequest.builder()
                    .keyword("")
                    .page(pageNumber)
                    .size(PAGE_SIZE)
                    .build();

            Page<T> result = query.apply(request);

            if (result == null || result.isEmpty()) {
                break;
            }

            results.addAll(result.getContent());

            if (!result.hasNext()) {
                break;
            }

            pageNumber++;
        }

        return results;
    }

    // =========================================================
    // DTO CONVERSION HELPERS
    // =========================================================

    private <T> T convertData(Object data, Class<T> dtoClass) {
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