
-- ============================================================
-- FILE: V4__create_indexes.sql
-- Mô tả: Tạo index phục vụ truy vấn NetworkMonitor
-- ============================================================


-- ------------------------------------------------------------
-- DEVICES
-- Hỗ trợ lọc thiết bị theo trạng thái.
-- ------------------------------------------------------------

CREATE INDEX idx_devices_status
ON devices(status);


-- ------------------------------------------------------------
-- DEVICE_CREDENTIALS
-- Tìm cấu hình đang hoạt động của một thiết bị.
-- ------------------------------------------------------------

CREATE INDEX idx_device_credentials_device_active
ON device_credentials(device_id, is_active);


-- ------------------------------------------------------------
-- OID_CONFIGS
-- Tìm cấu hình OID đang bật theo thiết bị.
-- ------------------------------------------------------------

CREATE INDEX idx_oid_configs_device_active
ON oid_configs(device_id, is_active);


-- ------------------------------------------------------------
-- METRICS
-- Lấy metrics của một thiết bị theo khoảng thời gian.
-- Index timestamp riêng hỗ trợ truy vấn theo thời gian
-- trên toàn bộ thiết bị.
-- ------------------------------------------------------------

CREATE INDEX idx_metrics_device_timestamp
ON metrics(device_id, timestamp DESC);

CREATE INDEX idx_metrics_timestamp
ON metrics(timestamp DESC);


-- ------------------------------------------------------------
-- METRIC_AGGREGATE
-- UNIQUE INDEX ngăn tạo hai bản ghi cùng thiết bị,
-- cùng khoảng tổng hợp và cùng thời điểm bắt đầu.
-- ------------------------------------------------------------

CREATE UNIQUE INDEX idx_metric_aggregate_period
ON metric_aggregate(device_id, aggregation_window, period_start);

CREATE INDEX idx_metric_aggregate_period_start
ON metric_aggregate(period_start DESC);


-- ------------------------------------------------------------
-- SYSLOGS
-- Tra cứu Syslog theo thời gian hoặc theo thiết bị và thời gian.
-- ------------------------------------------------------------

CREATE INDEX idx_syslogs_received_at
ON syslogs(received_at DESC);

CREATE INDEX idx_syslogs_device_received_at
ON syslogs(device_id, received_at DESC);


-- ------------------------------------------------------------
-- THRESHOLD_RULES
-- Tìm quy tắc đang bật của một thiết bị.
-- ------------------------------------------------------------

CREATE INDEX idx_threshold_rules_device_enabled
ON threshold_rules(device_id, is_enabled);


-- ------------------------------------------------------------
-- SYSLOG_RULES
-- Tìm các quy tắc Syslog đang bật theo thiết bị.
-- Quy tắc toàn hệ thống có device_id IS NULL.
-- ------------------------------------------------------------

CREATE INDEX idx_syslog_rules_device_enabled
ON syslog_rules(device_id, is_enabled);


-- ------------------------------------------------------------
-- INCIDENTS
-- Tìm incident theo trạng thái, thời gian, thiết bị
-- hoặc các bản ghi nguồn đã tạo ra incident.
-- ------------------------------------------------------------

CREATE INDEX idx_incidents_status_created_at
ON incidents(status, created_at DESC);

CREATE INDEX idx_incidents_device
ON incidents(device_id);

CREATE INDEX idx_incidents_threshold_rule
ON incidents(threshold_rule_id);

CREATE INDEX idx_incidents_syslog_rule
ON incidents(syslog_rule_id);

CREATE INDEX idx_incidents_syslog
ON incidents(syslog_id);


-- ------------------------------------------------------------
-- ARP_MAC_TABLES
-- Tra cứu thiết bị theo địa chỉ IP hoặc địa chỉ MAC.
-- ------------------------------------------------------------

CREATE INDEX idx_arp_mac_tables_ip
ON arp_mac_tables(ip_address);

CREATE INDEX idx_arp_mac_tables_mac
ON arp_mac_tables(mac_address);


-- ------------------------------------------------------------
-- AUDIT_LOGS
-- Tra cứu lịch sử theo thời gian hoặc theo người dùng.
-- ------------------------------------------------------------

CREATE INDEX idx_audit_logs_timestamp
ON audit_logs(timestamp DESC);

CREATE INDEX idx_audit_logs_user_timestamp
ON audit_logs(user_id, timestamp DESC);
