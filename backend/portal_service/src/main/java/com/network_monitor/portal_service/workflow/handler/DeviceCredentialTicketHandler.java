package com.network_monitor.portal_service.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceCredential;
import com.network_monitor.portal_service.repository.DeviceCredentialRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.SnmpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeviceCredentialTicketHandler implements TicketExecutionHandler {

    private final DeviceCredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;
    private final SnmpService snmpService;
    private final ObjectMapper objectMapper;

    @Override
    public String getSupportedEntityType() {
        return "DEVICE_CREDENTIAL";
    }

    @Override
    public void execute(ApprovalTicket ticket) throws Exception {
        switch (ticket.getActionType()) {
            case "CREATE" -> {
                DeviceCredentialRequest req = objectMapper.readValue(ticket.getPayload(), DeviceCredentialRequest.class);
                saveCredential(req);
            }
            case "BULK_IMPORT" -> {
                List<DeviceCredentialRequest> list = objectMapper.readValue(
                        ticket.getPayload(), new TypeReference<>() {}
                );
                for (DeviceCredentialRequest req : list) {
                    saveCredential(req);
                }
            }
            case "UPDATE" -> {
                DeviceCredentialRequest req = objectMapper.readValue(ticket.getPayload(), DeviceCredentialRequest.class);
                DeviceCredential existing = credentialRepository.findById(ticket.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("Credential không còn tồn tại"));
                Device device = deviceRepository.findById(req.getDeviceId())
                        .orElseThrow(() -> new IllegalArgumentException("Thiết bị không còn tồn tại"));

                existing.setDevice(device);
                existing.setCredentialName(req.getCredentialName());
                existing.setCredentialType(req.getCredentialType());
                existing.setProtocolType(req.getProtocolType());
                existing.setCommunityString(req.getCommunityString());
                existing.setPort(req.getPort());
                existing.setIsActive(req.getIsActive() == null ? Boolean.TRUE : req.getIsActive());
                credentialRepository.saveAndFlush(existing);
            }
            case "DELETE" -> {
                DeviceCredential credential = credentialRepository.findById(ticket.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("Credential không còn tồn tại"));
                credentialRepository.delete(credential);
                credentialRepository.flush();
            }
            case "BULK_DELETE" -> {
                List<DeviceCredential> list = credentialRepository.findByDevice_Id(ticket.getParentDeviceId());
                if (!list.isEmpty()) {
                    credentialRepository.deleteAll(list);
                    credentialRepository.flush();
                }
            }
            default -> throw new IllegalArgumentException("Action không hỗ trợ: " + ticket.getActionType());
        }
        snmpService.reloadConfigs();
    }

    private void saveCredential(DeviceCredentialRequest req) {
        Device device = deviceRepository.findById(req.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị ID=" + req.getDeviceId()));
        DeviceCredential credential = DeviceCredential.builder()
                .device(device)
                .credentialName(req.getCredentialName())
                .credentialType(req.getCredentialType())
                .protocolType(req.getProtocolType())
                .communityString(req.getCommunityString())
                .port(req.getPort())
                .isActive(req.getIsActive() == null ? Boolean.TRUE : req.getIsActive())
                .build();
        credentialRepository.saveAndFlush(credential);
    }
}