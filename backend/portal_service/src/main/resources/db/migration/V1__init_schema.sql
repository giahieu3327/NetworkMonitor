
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(150),
    phone_number VARCHAR(20),
    role_name VARCHAR(50) NOT NULL DEFAULT 'GUEST_VIEWER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    device_name VARCHAR(150) NOT NULL UNIQUE,
    ip_address INET NOT NULL UNIQUE,
    device_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE device_credentials (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    credential_name VARCHAR(100) NOT NULL,
    credential_type VARCHAR(20) NOT NULL,
    protocol_type VARCHAR(20) NOT NULL,
    community_string TEXT,
    port INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_device_credentials_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);

CREATE TABLE device_interfaces (
    device_id BIGINT NOT NULL,
    interface_index INTEGER NOT NULL,

    interface_name VARCHAR(150),
    interface_type INTEGER,
    mtu INTEGER,
    speed_bps BIGINT,

    admin_status VARCHAR(30),
    oper_status VARCHAR(30),

    phys_address VARCHAR(64),
    interface_alias TEXT,

    ip_address INET,
    subnet_mask INET,

    is_monitor BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_device_interfaces
        PRIMARY KEY (device_id, interface_index),

    CONSTRAINT fk_device_interfaces_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);

CREATE TABLE oid_configs (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    interface_index INTEGER,
    metric_scope VARCHAR(20) NOT NULL,
    metric_type VARCHAR(100) NOT NULL,
    oid_pattern TEXT NOT NULL,
    oid_kind VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    data_type VARCHAR(30) NOT NULL,
    multiplier DOUBLE PRECISION NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_oid_configs_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_oid_configs_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE CASCADE
);

CREATE TABLE metrics (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    metric_data JSONB NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_metrics_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);

CREATE TABLE metric_aggregate (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    aggregation_window VARCHAR(10) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    aggregated_data JSONB NOT NULL,

    CONSTRAINT fk_metric_aggregate_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);

CREATE TABLE threshold_rules (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    interface_index INTEGER,
    rule_name VARCHAR(150) NOT NULL,
    metric_type VARCHAR(100) NOT NULL,
    warning_limit DOUBLE PRECISION,
    critical_limit DOUBLE PRECISION,
    consecutive_occurrences INTEGER NOT NULL DEFAULT 3,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_threshold_rules_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_threshold_rules_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE CASCADE
);

CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT,
    threshold_rule_id BIGINT,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    camunda_process_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,

    CONSTRAINT fk_incidents_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_threshold_rule
        FOREIGN KEY (threshold_rule_id)
        REFERENCES threshold_rules(id)
        ON DELETE SET NULL
);

CREATE TABLE arp_mac_tables (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    interface_index INTEGER,

    ip_address INET NOT NULL,
    mac_address MACADDR NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_arp_mac_tables_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_arp_mac_tables_device_ip
        UNIQUE (device_id, ip_address)
);
