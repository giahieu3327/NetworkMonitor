-- ============================================================
-- FILE: V1__init_schema.sql
-- Mô tả: Tạo toàn bộ bảng cho NetworkMonitor
-- ============================================================


-- ============================================================
-- 1. USERS
-- ============================================================

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,

    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,

    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(30),

    role_name VARCHAR(100) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- 2. DEVICES
-- Portal_service quản lý
-- ============================================================

CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,

    device_name VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45) NOT NULL UNIQUE,

    device_type VARCHAR(100) NOT NULL,
    model VARCHAR(255) NOT NULL,
    firmware_version VARCHAR(255) NOT NULL,

    status VARCHAR(100) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- 3. DEVICE CREDENTIALS
-- Portal_service quản lý
-- ============================================================

CREATE TABLE device_credentials (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT NOT NULL,

    protocol_type VARCHAR(100) NOT NULL,
    port INTEGER NOT NULL DEFAULT 161,

    community_string VARCHAR(500),

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_device_credentials_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_device_credentials_device_protocol
        UNIQUE (device_id, protocol_type)
);


-- ============================================================
-- 4. DEVICE INTERFACES
-- Monitor_service tự động discover
--
-- Không dùng id riêng.
-- Khóa chính:
--     (device_id, interface_index)
-- ============================================================

CREATE TABLE device_interfaces (
    device_id BIGINT NOT NULL,

    interface_index INTEGER NOT NULL,

    interface_name VARCHAR(255) NOT NULL,
    mac_address VARCHAR(50),
    speed_bps BIGINT,

    admin_status VARCHAR(100) NOT NULL,
    oper_status VARCHAR(100) NOT NULL,

    discovered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (device_id, interface_index),

    CONSTRAINT fk_device_interfaces_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);


-- ============================================================
-- 5. OID CONFIGS
-- Portal_service quản lý
-- ============================================================

CREATE TABLE oid_configs (
    id BIGSERIAL PRIMARY KEY,

    metric_scope VARCHAR(100) NOT NULL,
    metric_type VARCHAR(100) NOT NULL,

    oid_pattern VARCHAR(500) NOT NULL,
    data_type VARCHAR(100) NOT NULL,

    multiplier DOUBLE PRECISION NOT NULL DEFAULT 1,

    device_type VARCHAR(100),
    device_id BIGINT,
    interface_index INTEGER,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    description TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_oid_configs_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_oid_configs_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE CASCADE
);


-- ============================================================
-- 6. METRICS
-- Monitor_service thu thập metrics
-- ============================================================

CREATE TABLE metrics (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT NOT NULL,
    interface_index INTEGER,

    metrics JSONB NOT NULL,

    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_metrics_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_metrics_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE CASCADE
);


-- ============================================================
-- 7. METRIC AGGREGATE
-- Lưu min / avg / max theo khoảng thời gian
-- ============================================================

CREATE TABLE metric_aggregate (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT NOT NULL,
    interface_index INTEGER,

    aggregation_window VARCHAR(100) NOT NULL,

    metrics JSONB NOT NULL,

    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,

    CONSTRAINT fk_metric_aggregate_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_metric_aggregate_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE CASCADE
);


-- ============================================================
-- 8. SYSLOGS
-- ============================================================

CREATE TABLE syslogs (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT,

    ip_address VARCHAR(45) NOT NULL,

    facility VARCHAR(100) NOT NULL,
    severity VARCHAR(100) NOT NULL,

    app_name VARCHAR(255),

    message TEXT NOT NULL,
    raw_log TEXT NOT NULL,

    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_syslogs_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL
);


-- ============================================================
-- 9. THRESHOLD RULES
-- Portal_service quản lý
-- ============================================================

CREATE TABLE threshold_rules (
    id BIGSERIAL PRIMARY KEY,

    rule_name VARCHAR(255) NOT NULL UNIQUE,

    metric_type VARCHAR(100) NOT NULL,

    warning_limit DOUBLE PRECISION,
    critical_limit DOUBLE PRECISION,

    consecutive_occurrences INTEGER NOT NULL DEFAULT 3,
    duration_seconds INTEGER NOT NULL DEFAULT 300,

    device_id BIGINT,
    interface_index INTEGER,

    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_by VARCHAR(36),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_threshold_rules_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_threshold_rules_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE SET NULL,

    CONSTRAINT fk_threshold_rules_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- ============================================================
-- 10. SYSLOG RULES
-- Portal_service quản lý
-- ============================================================

CREATE TABLE syslog_rules (
    id BIGSERIAL PRIMARY KEY,

    rule_name VARCHAR(255) NOT NULL UNIQUE,

    match_severity VARCHAR(100),
    match_pattern TEXT,

    assign_severity VARCHAR(100) NOT NULL DEFAULT 'CRITICAL',

    device_id BIGINT,

    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_by VARCHAR(36),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_syslog_rules_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_syslog_rules_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- ============================================================
-- 11. INCIDENTS
-- ============================================================

CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT,

    rule_id BIGINT,
    syslog_rule_id BIGINT,
    syslog_id BIGINT,

    severity VARCHAR(100) NOT NULL,

    title VARCHAR(500) NOT NULL,
    message TEXT NOT NULL,

    camunda_process_id VARCHAR(255),

    status VARCHAR(100) NOT NULL,

    acknowledged_by VARCHAR(36),
    acknowledged_at TIMESTAMP,

    resolved_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_incidents_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_rule
        FOREIGN KEY (rule_id)
        REFERENCES threshold_rules(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_syslog_rule
        FOREIGN KEY (syslog_rule_id)
        REFERENCES syslog_rules(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_syslog
        FOREIGN KEY (syslog_id)
        REFERENCES syslogs(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_acknowledged_by
        FOREIGN KEY (acknowledged_by)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- ============================================================
-- 12. NOTIFICATION CONFIGS
-- ============================================================

CREATE TABLE notification_configs (
    id BIGSERIAL PRIMARY KEY,

    channel_name VARCHAR(255) NOT NULL UNIQUE,
    channel_type VARCHAR(100) NOT NULL,

    bot_token TEXT,
    chat_id VARCHAR(255),

    smtp_host VARCHAR(255),
    smtp_port INTEGER NOT NULL DEFAULT 587,
    smtp_username VARCHAR(255),
    smtp_password TEXT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- 13. TOPOLOGY MAPS
-- ============================================================

CREATE TABLE topology_maps (
    id BIGSERIAL PRIMARY KEY,

    map_name VARCHAR(255) NOT NULL UNIQUE,

    nodes_data JSONB NOT NULL,
    edges_data JSONB NOT NULL,

    updated_by VARCHAR(36),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_topology_maps_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- ============================================================
-- 14. ARP / MAC TABLES
-- ============================================================

CREATE TABLE arp_mac_tables (
    id BIGSERIAL PRIMARY KEY,

    ip_address VARCHAR(45) NOT NULL,
    mac_address VARCHAR(50) NOT NULL,

    device_id BIGINT NOT NULL,
    interface_index INTEGER,

    vlan_id INTEGER,

    last_seen TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_arp_mac_tables_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_arp_mac_tables_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE CASCADE
);


-- ============================================================
-- 15. AUDIT LOGS
-- ============================================================

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,

    user_id VARCHAR(36),

    action VARCHAR(255) NOT NULL,
    module VARCHAR(255) NOT NULL,

    ip_address VARCHAR(45),

    details JSONB NOT NULL,

    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);