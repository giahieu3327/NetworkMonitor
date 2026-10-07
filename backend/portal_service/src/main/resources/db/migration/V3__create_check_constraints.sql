-- ============================================================
-- FILE: V3__create_check_constraints.sql
-- Mô tả: Các CHECK constraint kiểm tra tính hợp lệ của dữ liệu
-- ============================================================


-- ============================================================
-- DEVICE CREDENTIALS
-- Port hợp lệ: 1 - 65535
-- ============================================================

ALTER TABLE device_credentials
ADD CONSTRAINT chk_credential_port
CHECK (port > 0 AND port <= 65535);


-- ============================================================
-- DEVICE INTERFACES
-- Interface index phải > 0
-- ============================================================

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_interface_index
CHECK (interface_index > 0);


-- Speed không được âm
-- ============================================================

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_interface_speed
CHECK (speed_bps IS NULL OR speed_bps >= 0);


-- ============================================================
-- THRESHOLD RULES
-- ============================================================

ALTER TABLE threshold_rules
ADD CONSTRAINT chk_warning_limit
CHECK (
    warning_limit IS NULL
    OR warning_limit >= 0
);


ALTER TABLE threshold_rules
ADD CONSTRAINT chk_critical_limit
CHECK (
    critical_limit IS NULL
    OR critical_limit >= 0
);


ALTER TABLE threshold_rules
ADD CONSTRAINT chk_threshold_limits
CHECK (
    warning_limit IS NULL
    OR critical_limit IS NULL
    OR critical_limit >= warning_limit
);


ALTER TABLE threshold_rules
ADD CONSTRAINT chk_consecutive_occurrences
CHECK (consecutive_occurrences > 0);


ALTER TABLE threshold_rules
ADD CONSTRAINT chk_duration_seconds
CHECK (duration_seconds >= 0);


-- ============================================================
-- NOTIFICATION CONFIGS
-- SMTP port nếu có thì phải nằm trong 1 - 65535
-- ============================================================

ALTER TABLE notification_configs
ADD CONSTRAINT chk_smtp_port
CHECK (
    smtp_port IS NULL
    OR (
        smtp_port > 0
        AND smtp_port <= 65535
    )
);


-- ============================================================
-- ARP / MAC TABLES
-- VLAN hợp lệ: 1 - 4094
-- ============================================================

ALTER TABLE arp_mac_tables
ADD CONSTRAINT chk_vlan_id
CHECK (
    vlan_id IS NULL
    OR (
        vlan_id >= 1
        AND vlan_id <= 4094
    )
);


-- ============================================================
-- METRIC AGGREGATE
-- period_end phải lớn hơn period_start
-- ============================================================

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_period
CHECK (period_end > period_start);