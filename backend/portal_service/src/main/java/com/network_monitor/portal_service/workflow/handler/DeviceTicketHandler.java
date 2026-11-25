package com.network_monitor.portal_service.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.health.Health;
import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.SnmpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeviceTicketHandler implements TicketExecutionHandler {

    private final DeviceRepository deviceRepository;
    private final Health health;
    private final SnmpService snmpService;
    private final ObjectMapper objectMapper;

    @Override
    public String getSupportedEntityType() {
        return "DEVICE";
    }

    @Override
    public void execute(ApprovalTicket ticket) throws Exception {
        switch (ticket.getActionType()) {
            case "CREATE" -> {
                DeviceRequest req = objectMapper.readValue(ticket.getPayload(), DeviceRequest.class);
                saveDevice(req);
            }
            case "BULK_IMPORT" -> {
                List<DeviceRequest> list = objectMapper.readValue(
                        ticket.getPayload(), new TypeReference<>() {}
                );
                for (DeviceRequest req : list) {
                    saveDevice(req);
                }
            }
            case "UPDATE" -> {
                DeviceRequest req = objectMapper.readValue(ticket.getPayload(), DeviceRequest.class);
                Device device = deviceRepository.findById(ticket.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("Thiết bị không còn tồn tại"));
                device.setDeviceName(req.getDeviceName());
                device.setIpAddress(req.getIpAddress());
                device.setDeviceType(req.getDeviceType());
                device.setStatus(health.checkPing(req.getIpAddress()) ? "UP" : "DOWN");
                deviceRepository.saveAndFlush(device);
            }
            case "DELETE" -> {
                Device device = deviceRepository.findById(ticket.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("Thiết bị không còn tồn tại"));
                deviceRepository.delete(device);
                deviceRepository.flush();
            }
            case "BULK_DELETE" -> {
                deviceRepository.deleteAll();
                deviceRepository.flush();
            }
            default -> throw new IllegalArgumentException("Action không hỗ trợ: " + ticket.getActionType());
        }
        snmpService.reloadConfigs();
    }

    private void saveDevice(DeviceRequest req) {
        Device device = Device.builder()
                .deviceName(req.getDeviceName())
                .ipAddress(req.getIpAddress())
                .deviceType(req.getDeviceType())
                .status(health.checkPing(req.getIpAddress()) ? "UP" : "DOWN")
                .build();
        deviceRepository.saveAndFlush(device);
    }
}