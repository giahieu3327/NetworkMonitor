-- ============================================================
-- FILE: schema.sql (PostgreSQL DDL)
-- ============================================================

-- ============================================================
-- PHẦN 1: TẠO BẢNG (CREATE TABLES)
-- ============================================================

-- 1.1. NHÓM QUẢN LÝ USER LOCAL (Keycloak Profile)
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY, -- Keycloak sub UUID
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.2. NHÓM QUẢN LÝ THIẾT BỊ & CẤU HÌNH
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NOT NULL UNIQUE,
    device_type VARCHAR(30) NOT NULL,
    model VARCHAR(100),
    status VARCHAR(20) DEFAULT 'UP',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE device_credentials (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    protocol_type VARCHAR(20) NOT NULL,
    port INT NOT NULL,
    community_string VARCHAR(100),
    snmpv3_username VARCHAR(100),
    snmpv3_auth_pass VARCHAR(100),
    api_token TEXT,
    is_primary BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE device_interfaces (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    interface_index INT NOT NULL,
    interface_name VARCHAR(50) NOT NULL,
    mac_address VARCHAR(17),
    speed_bps BIGINT DEFAULT 1000000000,
    admin_status VARCHAR(10) DEFAULT 'UP',
    oper_status VARCHAR(10) DEFAULT 'UP',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE threshold_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    metric_type VARCHAR(30) NOT NULL,
    warning_limit DOUBLE PRECISION NOT NULL,
    critical_limit DOUBLE PRECISION NOT NULL,
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.3. NHÓM THU THẬP METRICS REAL-TIME (Time-Series)
CREATE TABLE device_metrics (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    cpu_usage_pct DOUBLE PRECISION,
    ram_usage_pct DOUBLE PRECISION,
    temperature_c DOUBLE PRECISION,
    active_sessions INT,
    uptime_seconds BIGINT,
    latency_ms DOUBLE PRECISION,
    packet_loss_pct DOUBLE PRECISION,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE interface_metrics (
    id BIGSERIAL PRIMARY KEY,
    interface_id BIGINT NOT NULL REFERENCES device_interfaces(id) ON DELETE CASCADE,
    bytes_in_bps BIGINT DEFAULT 0,
    bytes_out_bps BIGINT DEFAULT 0,
    packets_in BIGINT DEFAULT 0,
    packets_out BIGINT DEFAULT 0,
    errors_in INT DEFAULT 0,
    errors_out INT DEFAULT 0,
    discards_in INT DEFAULT 0,
    discards_out INT DEFAULT 0,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 1.4. NHÓM CẢNH BÁO & CAMUNDA WORKFLOW
CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    rule_id BIGINT REFERENCES threshold_rules(id) ON DELETE SET NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    camunda_process_id VARCHAR(64),
    status VARCHAR(30) DEFAULT 'OPEN',
    acknowledged_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_configs (
    id BIGSERIAL PRIMARY KEY,
    channel_name VARCHAR(50) NOT NULL,
    channel_type VARCHAR(20) NOT NULL,
    bot_token VARCHAR(255),
    chat_id VARCHAR(100),
    smtp_host VARCHAR(100),
    smtp_port INT,
    smtp_username VARCHAR(100),
    smtp_password VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.5. NHÓM TOPOLOGY & TRA CỨU IPAM
CREATE TABLE topology_maps (
    id BIGSERIAL PRIMARY KEY,
    map_name VARCHAR(100) NOT NULL,
    nodes_data JSONB NOT NULL,
    edges_data JSONB NOT NULL,
    updated_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE arp_mac_tables (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    mac_address VARCHAR(17) NOT NULL,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    interface_name VARCHAR(50) NOT NULL,
    vlan_id INT,
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.6. NHÓM AUDIT LOGS
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    module VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    details TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);


-- ============================================================
-- PHẦN 2: FUNCTION TRIGGER TỰ ĐỘNG CẬP NHẬT updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';


-- ============================================================
-- PHẦN 3: CREATE TRIGGER
-- ============================================================

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_devices_updated_at
    BEFORE UPDATE ON devices
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_credentials_updated_at
    BEFORE UPDATE ON device_credentials
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_interfaces_updated_at
    BEFORE UPDATE ON device_interfaces
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_threshold_rules_updated_at
    BEFORE UPDATE ON threshold_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_incidents_updated_at
    BEFORE UPDATE ON incidents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_configs_updated_at
    BEFORE UPDATE ON notification_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_topology_maps_updated_at
    BEFORE UPDATE ON topology_maps
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_arp_mac_tables_updated_at
    BEFORE UPDATE ON arp_mac_tables
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- PHẦN 4: CHECK CONSTRAINTS VÀ INDEXES
-- ============================================================

-- 4.1. KHU VỰC RÀNG BUỘC CHECK CONSTRAINTS (ALTER TABLE)

-- Check cho bảng devices
ALTER TABLE devices ADD CONSTRAINT chk_device_type CHECK (device_type IN ('CORE_SWITCH', 'DISTRIBUTION_SWITCH', 'FIREWALL', 'ROUTER'));
ALTER TABLE devices ADD CONSTRAINT chk_device_status CHECK (status IN ('UP', 'DOWN', 'WARNING'));

-- Check cho bảng device_credentials
ALTER TABLE device_credentials ADD CONSTRAINT chk_protocol_type CHECK (protocol_type IN ('SNMP_V2C', 'SNMP_V3', 'REST_API'));
ALTER TABLE device_credentials ADD CONSTRAINT chk_credential_port CHECK (port > 0 AND port <= 65535);

-- Check cho bảng device_interfaces
ALTER TABLE device_interfaces ADD CONSTRAINT chk_interface_speed CHECK (speed_bps >= 0);
ALTER TABLE device_interfaces ADD CONSTRAINT chk_interface_admin_status CHECK (admin_status IN ('UP', 'DOWN'));
ALTER TABLE device_interfaces ADD CONSTRAINT chk_interface_oper_status CHECK (oper_status IN ('UP', 'DOWN'));

-- Check cho bảng threshold_rules
ALTER TABLE threshold_rules ADD CONSTRAINT chk_metric_type CHECK (metric_type IN ('CPU', 'RAM', 'BANDWIDTH', 'LATENCY', 'PACKET_LOSS', 'TEMPERATURE'));
ALTER TABLE threshold_rules ADD CONSTRAINT chk_warning_limit CHECK (warning_limit >= 0);
ALTER TABLE threshold_rules ADD CONSTRAINT chk_critical_limit CHECK (critical_limit >= 0);
ALTER TABLE threshold_rules ADD CONSTRAINT chk_threshold_limits CHECK (critical_limit >= warning_limit);

-- Check cho bảng device_metrics
ALTER TABLE device_metrics ADD CONSTRAINT chk_cpu_pct CHECK (cpu_usage_pct IS NULL OR (cpu_usage_pct >= 0 AND cpu_usage_pct <= 100));
ALTER TABLE device_metrics ADD CONSTRAINT chk_ram_pct CHECK (ram_usage_pct IS NULL OR (ram_usage_pct >= 0 AND ram_usage_pct <= 100));
ALTER TABLE device_metrics ADD CONSTRAINT chk_temp CHECK (temperature_c IS NULL OR (temperature_c >= -50 AND temperature_c <= 150));
ALTER TABLE device_metrics ADD CONSTRAINT chk_sessions CHECK (active_sessions IS NULL OR active_sessions >= 0);
ALTER TABLE device_metrics ADD CONSTRAINT chk_uptime CHECK (uptime_seconds IS NULL OR uptime_seconds >= 0);
ALTER TABLE device_metrics ADD CONSTRAINT chk_latency CHECK (latency_ms IS NULL OR latency_ms >= 0);
ALTER TABLE device_metrics ADD CONSTRAINT chk_packet_loss CHECK (packet_loss_pct IS NULL OR (packet_loss_pct >= 0 AND packet_loss_pct <= 100));

-- Check cho bảng interface_metrics
ALTER TABLE interface_metrics ADD CONSTRAINT chk_bytes_in CHECK (bytes_in_bps >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_bytes_out CHECK (bytes_out_bps >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_packets_in CHECK (packets_in >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_packets_out CHECK (packets_out >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_errors_in CHECK (errors_in >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_errors_out CHECK (errors_out >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_discards_in CHECK (discards_in >= 0);
ALTER TABLE interface_metrics ADD CONSTRAINT chk_discards_out CHECK (discards_out >= 0);

-- Check cho bảng incidents
ALTER TABLE incidents ADD CONSTRAINT chk_incident_severity CHECK (severity IN ('WARNING', 'CRITICAL', 'DOWN'));
ALTER TABLE incidents ADD CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'));

-- Check cho bảng notification_configs
ALTER TABLE notification_configs ADD CONSTRAINT chk_channel_type CHECK (channel_type IN ('TELEGRAM', 'EMAIL', 'WEBHOOK'));
ALTER TABLE notification_configs ADD CONSTRAINT chk_smtp_port CHECK (smtp_port IS NULL OR (smtp_port > 0 AND smtp_port <= 65535));

-- Check cho bảng arp_mac_tables
ALTER TABLE arp_mac_tables ADD CONSTRAINT chk_vlan_id CHECK (vlan_id IS NULL OR (vlan_id >= 1 AND vlan_id <= 4094));

-- 4.2. KHU VỰC TẠO INDEXES TỐI ƯU HIỆU NĂNG TRUY VẤN
CREATE INDEX idx_devices_ip ON devices(ip_address);
CREATE INDEX idx_device_credentials_device_id ON device_credentials(device_id);
CREATE INDEX idx_device_interfaces_device_id ON device_interfaces(device_id);
CREATE INDEX idx_device_metrics_device_time ON device_metrics(device_id, timestamp DESC);
CREATE INDEX idx_interface_metrics_iface_time ON interface_metrics(interface_id, timestamp DESC);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_device_id ON incidents(device_id);
CREATE INDEX idx_topology_nodes_jsonb ON topology_maps USING GIN (nodes_data);
CREATE INDEX idx_topology_edges_jsonb ON topology_maps USING GIN (edges_data);
CREATE INDEX idx_arp_mac_ip ON arp_mac_tables(ip_address);
CREATE INDEX idx_arp_mac_mac ON arp_mac_tables(mac_address);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);

