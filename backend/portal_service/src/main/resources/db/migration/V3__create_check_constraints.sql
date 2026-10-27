
-- ============================================================
-- FILE: V3__create_check_constraints.sql
-- Mô tả: Tạo CHECK constraint kiểm tra tính hợp lệ dữ liệu
-- ============================================================


-- ------------------------------------------------------------
-- DEVICES
-- Trạng thái thiết bị chỉ nhận các giá trị quy định.
-- ------------------------------------------------------------

ALTER TABLE devices
ADD CONSTRAINT chk_devices_status
CHECK (status IN ('UP', 'DOWN', 'UNKNOWN'));


-- ------------------------------------------------------------
-- DEVICE_CREDENTIALS
-- Kiểm tra loại credential, giao thức và số cổng.
-- SNMP dùng SNMPV1/SNMPV2C/SNMPV3.
-- SYSLOG dùng UDP/TCP/TLS.
-- ------------------------------------------------------------

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_type
CHECK (credential_type IN ('SNMP', 'SYSLOG'));

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_protocol
CHECK (
    protocol_type IN (
        'SNMPV1', 'SNMPV2C', 'SNMPV3',
        'UDP', 'TCP', 'TLS'
    )
);

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_port
CHECK (port IS NULL OR port BETWEEN 1 AND 65535);

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_protocol_match
CHECK (
    (credential_type = 'SNMP'
        AND protocol_type IN ('SNMPV1', 'SNMPV2C', 'SNMPV3'))
    OR
    (credential_type = 'SYSLOG'
        AND protocol_type IN ('UDP', 'TCP', 'TLS'))
);


-- ------------------------------------------------------------
-- DEVICE_INTERFACES
-- Tốc độ interface không được âm.
-- ------------------------------------------------------------

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_speed
CHECK (speed_bps IS NULL OR speed_bps >= 0);


-- ------------------------------------------------------------
-- OID_CONFIGS
-- Kiểm tra phạm vi cấu hình và hệ số chuyển đổi.
-- DEVICE: interface_index phải NULL.
-- INTERFACE: interface_index phải có giá trị.
-- ------------------------------------------------------------

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_scope
CHECK (metric_scope IN ('DEVICE', 'INTERFACE'));

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_interface_scope
CHECK (
    (metric_scope = 'DEVICE' AND interface_index IS NULL)
    OR
    (metric_scope = 'INTERFACE' AND interface_index IS NOT NULL)
);

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_multiplier
CHECK (multiplier > 0);


-- ------------------------------------------------------------
-- METRICS
-- metric_data phải là JSON object.
-- ------------------------------------------------------------

ALTER TABLE metrics
ADD CONSTRAINT chk_metrics_data_object
CHECK (jsonb_typeof(metric_data) = 'object');


-- ------------------------------------------------------------
-- METRIC_AGGREGATE
-- Kiểm tra loại khoảng tổng hợp, dữ liệu JSON và thời gian.
-- ------------------------------------------------------------

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_window
CHECK (aggregation_window IN ('1H', '6H', '1D'));

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_period
CHECK (period_end > period_start);

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_data_object
CHECK (jsonb_typeof(aggregated_data) = 'object');


-- ------------------------------------------------------------
-- SYSLOGS
-- Severity gốc theo các mức Syslog tiêu chuẩn.
-- UNKNOWN dùng khi không xác định được mức độ.
-- ------------------------------------------------------------

ALTER TABLE syslogs
ADD CONSTRAINT chk_syslogs_severity
CHECK (
    severity IN (
        'EMERGENCY', 'ALERT', 'CRITICAL', 'ERROR',
        'WARNING', 'NOTICE', 'INFO', 'DEBUG', 'UNKNOWN'
    )
);


-- ------------------------------------------------------------
-- THRESHOLD_RULES
-- Số lần liên tiếp phải lớn hơn 0.
-- Điều kiện warning < critical áp dụng cho chỉ số
-- mà giá trị càng cao thì mức độ càng nghiêm trọng.
-- ------------------------------------------------------------

ALTER TABLE threshold_rules
ADD CONSTRAINT chk_threshold_rules_occurrences
CHECK (consecutive_occurrences > 0);

ALTER TABLE threshold_rules
ADD CONSTRAINT chk_threshold_rules_limits
CHECK (
    warning_limit IS NULL
    OR critical_limit IS NULL
    OR warning_limit < critical_limit
);


-- ------------------------------------------------------------
-- SYSLOG_RULES
-- Kiểm tra severity dùng để lọc Syslog và severity
-- được gán cho incident.
-- ------------------------------------------------------------

ALTER TABLE syslog_rules
ADD CONSTRAINT chk_syslog_rules_match_severity
CHECK (
    match_severity IS NULL
    OR match_severity IN (
        'EMERGENCY', 'ALERT', 'CRITICAL', 'ERROR',
        'WARNING', 'NOTICE', 'INFO', 'DEBUG', 'UNKNOWN'
    )
);

ALTER TABLE syslog_rules
ADD CONSTRAINT chk_syslog_rules_assign_severity
CHECK (assign_severity IN ('INFO', 'WARNING', 'CRITICAL'));


-- ------------------------------------------------------------
-- INCIDENTS
-- Kiểm tra severity, trạng thái và thời điểm giải quyết.
-- ------------------------------------------------------------

ALTER TABLE incidents
ADD CONSTRAINT chk_incidents_severity
CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'));

ALTER TABLE incidents
ADD CONSTRAINT chk_incidents_status
CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'));

ALTER TABLE incidents
ADD CONSTRAINT chk_incidents_resolved_at
CHECK (
    resolved_at IS NULL
    OR resolved_at >= created_at
);


-- ------------------------------------------------------------
-- TOPOLOGY_MAPS
-- nodes_data và edges_data phải là JSON array.
-- ------------------------------------------------------------

ALTER TABLE topology_maps
ADD CONSTRAINT chk_topology_maps_nodes_array
CHECK (jsonb_typeof(nodes_data) = 'array');

ALTER TABLE topology_maps
ADD CONSTRAINT chk_topology_maps_edges_array
CHECK (jsonb_typeof(edges_data) = 'array');


-- ------------------------------------------------------------
-- ARP_MAC_TABLES
-- VLAN ID hợp lệ nằm trong khoảng 1 đến 4094.
-- NULL nghĩa là không có thông tin VLAN.
-- ------------------------------------------------------------

ALTER TABLE arp_mac_tables
ADD CONSTRAINT chk_arp_mac_tables_vlan
CHECK (vlan_id IS NULL OR vlan_id BETWEEN 1 AND 4094);
