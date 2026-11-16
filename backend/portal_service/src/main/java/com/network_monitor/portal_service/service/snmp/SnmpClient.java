package com.network_monitor.portal_service.service.snmp;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.model.dto.snmp.WalkResult;

import lombok.extern.slf4j.Slf4j;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SnmpClient {

    private static final long SNMP_TIMEOUT = 3000;
    private static final int SNMP_RETRIES = 1;
    private static final int MAX_WALK_ROWS = 10000;

    // ==================== GET ====================

    public String get(
            String ipAddress,
            List<MetricConfig.Credential> credentials,
            String oid
    ) {

        if (credentials == null || credentials.isEmpty()) {
            return null;
        }

        for (MetricConfig.Credential credential : credentials) {

            if (!isUsable(credential)) {
                continue;
            }

            try {
                String value = sendGet(ipAddress, credential, oid);

                if (value != null) {
                    return value;
                }

            } catch (Exception e) {
                log.debug("SNMP GET failed: ip={}, oid={}, error={}",
                        ipAddress, oid, e.getMessage());
            }
        }

        return null;
    }

    private String sendGet(
            String ipAddress,
            MetricConfig.Credential credential,
            String oid
    ) throws Exception {

        Address address = GenericAddress.parse(
                "udp:" + ipAddress + "/" + credential.getPort()
        );

        if (address == null) {
            return null;
        }

        try (DefaultUdpTransportMapping transport =
                     new DefaultUdpTransportMapping();
             Snmp snmp = new Snmp(transport)) {

            transport.listen();

            Target<?> target = createTarget(address, credential);

            PDU request = new PDU();
            request.add(new VariableBinding(new OID(oid)));
            request.setType(PDU.GET);

            ResponseEvent<?> event = snmp.send(request, target);

            if (event == null || event.getResponse() == null) {
                return null;
            }

            PDU response = event.getResponse();

            if (response.getErrorStatus() != PDU.noError
                    || response.size() == 0) {
                return null;
            }

            VariableBinding binding = response.get(0);

            if (binding.isException()) {
                return null;
            }

            return binding.getVariable().toString();
        }
    }

    // ==================== WALK ====================

    public WalkResult walk(
            String ipAddress,
            List<MetricConfig.Credential> credentials,
            String oid
    ) {

        if (credentials == null || credentials.isEmpty()) {
            return new WalkResult(Map.of(), false);
        }

        for (MetricConfig.Credential credential : credentials) {

            if (!isUsable(credential)) {
                continue;
            }

            try {
                WalkResult result = sendWalk(ipAddress, credential, oid);

                // WALK thành công kể cả khi không có dữ liệu.
                if (result.success()) {
                    return result;
                }

            } catch (Exception e) {
                log.debug("SNMP WALK failed: ip={}, oid={}, error={}",
                        ipAddress, oid, e.getMessage());
            }
        }

        return new WalkResult(Map.of(), false);
    }

    private WalkResult sendWalk(
            String ipAddress,
            MetricConfig.Credential credential,
            String rootOid
    ) throws Exception {

        Address address = GenericAddress.parse(
                "udp:" + ipAddress + "/" + credential.getPort()
        );

        if (address == null) {
            return new WalkResult(Map.of(), false);
        }

        OID root = new OID(rootOid);
        OID lastOid = root;

        Map<String, Object> result = new LinkedHashMap<>();

        try (DefaultUdpTransportMapping transport =
                     new DefaultUdpTransportMapping();
             Snmp snmp = new Snmp(transport)) {

            transport.listen();

            Target<?> target = createTarget(address, credential);

            for (int i = 0; i < MAX_WALK_ROWS; i++) {

                PDU request = new PDU();
                request.add(new VariableBinding(lastOid));
                request.setType(PDU.GETNEXT);

                ResponseEvent<?> event = snmp.send(request, target);

                if (event == null || event.getResponse() == null) {
                    return new WalkResult(result, false);
                }

                PDU response = event.getResponse();

                if (response.getErrorStatus() != PDU.noError
                        || response.size() == 0) {
                    return new WalkResult(result, false);
                }

                VariableBinding binding = response.get(0);

                if (binding.isException()) {
                    return new WalkResult(result, false);
                }

                OID currentOid = binding.getOid();

                // Đã đi ra ngoài nhánh OID cần WALK.
                if (!currentOid.startsWith(root)) {
                    return new WalkResult(result, true);
                }

                // Phát hiện OID không tăng.
                if (currentOid.compareTo(lastOid) <= 0) {
                    return new WalkResult(result, false);
                }

                result.put(
                        currentOid.toString(),
                        binding.getVariable().toString()
                );

                lastOid = currentOid;
            }

            // Vượt quá giới hạn số dòng WALK.
            return new WalkResult(result, false);
        }
    }

    // ==================== TARGET ====================

    private Target<?> createTarget(
            Address address,
            MetricConfig.Credential credential
    ) {

        CommunityTarget<Address> target = new CommunityTarget<>();

        target.setAddress(address);
        target.setCommunity(
                new OctetString(credential.getCommunityString())
        );

        target.setVersion(getSnmpVersion(credential.getProtocolType()));
        target.setTimeout(SNMP_TIMEOUT);
        target.setRetries(SNMP_RETRIES);

        return target;
    }

    // ==================== CREDENTIAL ====================

    private boolean isUsable(MetricConfig.Credential credential) {

        if (credential == null
                || credential.getProtocolType() == null
                || credential.getCommunityString() == null
                || credential.getCommunityString().isBlank()
                || credential.getPort() == null
                || credential.getPort() < 1
                || credential.getPort() > 65535) {
            return false;
        }

        String protocol = credential.getProtocolType()
                .trim()
                .toUpperCase();

        return protocol.equals("SNMPV1")
                || protocol.equals("V1")
                || protocol.equals("SNMPV2C")
                || protocol.equals("V2C");
    }

    private int getSnmpVersion(String protocolType) {

        String protocol = protocolType.trim().toUpperCase();

        return switch (protocol) {
            case "SNMPV1", "V1" -> SnmpConstants.version1;
            case "SNMPV2C", "V2C" -> SnmpConstants.version2c;
            default -> throw new IllegalArgumentException(
                    "Unsupported SNMP protocol: " + protocolType
            );
        };
    }
}