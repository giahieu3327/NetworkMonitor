package com.network_monitor.portal_service.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceInterface;
import com.network_monitor.portal_service.model.entity.OidConfig;
import com.network_monitor.portal_service.repository.DeviceInterfaceRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.OidConfigRepository;
import com.network_monitor.portal_service.service.SnmpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OidConfigTicketHandler implements TicketExecutionHandler {

    private final OidConfigRepository oidConfigRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceInterfaceRepository deviceInterfaceRepository;
    private final SnmpService snmpService;
    private final ObjectMapper objectMapper;

    @Override
    public String getSupportedEntityType() {
        return "OID_CONFIG";
    }

    @Override
    public void execute(ApprovalTicket ticket) throws Exception {
        switch (ticket.getActionType()) {
            case "CREATE" -> {
                OidConfigRequest req = objectMapper.readValue(ticket.getPayload(), OidConfigRequest.class);
                saveOidConfig(req);
            }
            case "BULK_IMPORT" -> {
                List<OidConfigRequest> list = objectMapper.readValue(
                        ticket.getPayload(), new TypeReference<>() {}
                );
                for (OidConfigRequest req : list) {
                    saveOidConfig(req);
                }
            }
            case "UPDATE" -> {
                OidConfigRequest req = objectMapper.readValue(ticket.getPayload(), OidConfigRequest.class);
                OidConfig config = oidConfigRepository.findById(ticket.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("OID Config không còn tồn tại"));

                if (req.getMetricScope() != null) config.setMetricScope(req.getMetricScope());
                if (req.getMetricType() != null) config.setMetricType(req.getMetricType());
                if (req.getOidKind() != null) config.setOidKind(req.getOidKind().trim().toUpperCase());
                if (req.getOidPattern() != null) config.setOidPattern(req.getOidPattern());
                if (req.getDataType() != null) config.setDataType(req.getDataType());
                if (req.getMultiplier() != null) config.setMultiplier(req.getMultiplier());
                if (req.getIsActive() != null) config.setIsActive(req.getIsActive());

                Device device = req.getDeviceId() != null
                        ? deviceRepository.findById(req.getDeviceId())
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị"))
                        : config.getDevice();
                config.setDevice(device);

                Integer effectiveInterfaceIndex = req.getInterfaceIndex() != null
                        ? req.getInterfaceIndex()
                        : (req.getDeviceId() != null ? null : config.getInterfaceIndex());
                bindInterface(config, device, effectiveInterfaceIndex);

                oidConfigRepository.saveAndFlush(config);
            }
            case "DELETE" -> {
                OidConfig config = oidConfigRepository.findById(ticket.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("OID Config không còn tồn tại"));
                oidConfigRepository.delete(config);
                oidConfigRepository.flush();
            }
            case "BULK_DELETE" -> {
                List<OidConfig> list = oidConfigRepository.findByDevice_Id(ticket.getParentDeviceId());
                if (!list.isEmpty()) {
                    oidConfigRepository.deleteAll(list);
                    oidConfigRepository.flush();
                }
            }
            default -> throw new IllegalArgumentException("Action không hỗ trợ: " + ticket.getActionType());
        }
        snmpService.reloadConfigs();
    }

    private void saveOidConfig(OidConfigRequest req) {
        Device device = deviceRepository.findById(req.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị ID=" + req.getDeviceId()));

        OidConfig config = OidConfig.builder()
                .metricScope(req.getMetricScope())
                .metricType(req.getMetricType())
                .oidKind(req.getOidKind().trim().toUpperCase())
                .oidPattern(req.getOidPattern())
                .dataType(req.getDataType())
                .multiplier(req.getMultiplier() == null ? 1.0 : req.getMultiplier())
                .device(device)
                .isActive(req.getIsActive() == null ? Boolean.TRUE : req.getIsActive())
                .build();

        bindInterface(config, device, req.getInterfaceIndex());
        oidConfigRepository.saveAndFlush(config);
    }

    private void bindInterface(OidConfig config, Device device, Integer interfaceIndex) {
        config.setInterfaceIndex(interfaceIndex);
        if (interfaceIndex == null) {
            config.setDeviceInterface(null);
            return;
        }
        DeviceInterface.DeviceInterfaceId id = new DeviceInterface.DeviceInterfaceId(device.getId(), interfaceIndex);
        DeviceInterface iface = deviceInterfaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy interface " + interfaceIndex));
        config.setDeviceInterface(iface);
    }
}