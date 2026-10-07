-- ============================================================
-- FILE: V4__create_indexes.sql
-- Mô tả: Index phục vụ truy vấn NetworkMonitor
-- ============================================================


-- ============================================================
-- DEVICE CREDENTIALS
-- ============================================================

CREATE INDEX idx_device_credentials_device
ON device_credentials(device_id);


-- ============================================================
-- DEVICE INTERFACES
-- ============================================================

CREATE INDEX idx_device_interfaces_device
ON device_interfaces(device_id);

CREATE INDEX idx_device_interfaces_mac
ON device_interfaces(mac_address);


-- ============================================================
-- OID CONFIGS
-- ============================================================

CREATE INDEX idx_oid_configs_device
ON oid_configs(device_id);

CREATE INDEX idx_oid_configs_device_interface
ON oid_configs(device_id, interface_index);

CREATE INDEX idx_oid_configs_metric_type
ON oid_configs(metric_type);


-- ============================================================
-- METRICS
--
-- Phục vụ:
--   device + time range
--   interface + time range
-- ============================================================

CREATE INDEX idx_metrics_device_time
ON metrics(device_id, timestamp);

CREATE INDEX idx_metrics_interface_time
ON metrics(device_id, interface_index, timestamp);


-- ============================================================
-- METRIC AGGREGATE
--
-- Phục vụ historical chart:
--   device + aggregation window + thời gian
--   interface + aggregation window + thời gian
-- ============================================================

CREATE INDEX idx_metric_aggregate_device_window_time
ON metric_aggregate(
    device_id,
    aggregation_window,
    period_start
);

CREATE INDEX idx_metric_aggregate_interface_window_time
ON metric_aggregate(
    device_id,
    interface_index,
    aggregation_window,
    period_start
);


-- ============================================================
-- SYSLOGS
-- ============================================================

CREATE INDEX idx_syslogs_device_time
ON syslogs(device_id, timestamp);

CREATE INDEX idx_syslogs_severity_time
ON syslogs(severity, timestamp);


-- ============================================================
-- THRESHOLD RULES
-- ============================================================

CREATE INDEX idx_threshold_rules_device
ON threshold_rules(device_id);

CREATE INDEX idx_threshold_rules_metric_type
ON threshold_rules(metric_type);

CREATE INDEX idx_threshold_rules_enabled
ON threshold_rules(is_enabled);


-- ============================================================
-- SYSLOG RULES
-- ============================================================

CREATE INDEX idx_syslog_rules_device
ON syslog_rules(device_id);

CREATE INDEX idx_syslog_rules_enabled
ON syslog_rules(is_enabled);


-- ============================================================
-- INCIDENTS
-- ============================================================

CREATE INDEX idx_incidents_device_time
ON incidents(device_id, created_at);

CREATE INDEX idx_incidents_status
ON incidents(status);

CREATE INDEX idx_incidents_severity
ON incidents(severity);


-- ============================================================
-- ARP / MAC TABLES
-- ============================================================

CREATE INDEX idx_arp_mac_ip
ON arp_mac_tables(ip_address);

CREATE INDEX idx_arp_mac_mac
ON arp_mac_tables(mac_address);

CREATE INDEX idx_arp_mac_device_interface
ON arp_mac_tables(device_id, interface_index);


-- ============================================================
-- AUDIT LOGS
-- ============================================================

CREATE INDEX idx_audit_logs_user_time
ON audit_logs(user_id, timestamp);

CREATE INDEX idx_audit_logs_module_time
ON audit_logs(module, timestamp);