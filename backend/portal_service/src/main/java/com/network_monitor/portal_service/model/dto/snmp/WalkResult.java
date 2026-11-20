package com.network_monitor.portal_service.model.dto.snmp;

import java.util.Map;

public record WalkResult(
        Map<String, Object> values,
        boolean success
) {}