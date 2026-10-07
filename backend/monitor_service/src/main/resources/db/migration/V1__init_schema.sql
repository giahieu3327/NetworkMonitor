-- ============================================================
-- FILE: V1__init_schema.sql
-- Database: SQLite
-- Service: monitor_service
--
-- Portal quản lý:
--   devices
--   device_credentials
--   oid_configs
--
-- Monitor quản lý:
--   device_interfaces
--   metrics
--   syslogs
--
-- Monitor -> Portal:
--   sync_queue
-- ============================================================

-- ============================================================
-- 1. DEVICES
-- ============================================================

CREATE TABLE devices (
    id INTEGER PRIMARY KEY,

    device_name TEXT NOT NULL UNIQUE,

    ip_address TEXT NOT NULL UNIQUE,

    device_type TEXT NOT NULL,

    model TEXT NOT NULL,

    firmware_version TEXT NOT NULL,

    status TEXT NOT NULL,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- 2. DEVICE CREDENTIALS
-- ============================================================

CREATE TABLE device_credentials (
    id INTEGER PRIMARY KEY,

    device_id INTEGER NOT NULL,

    protocol_type TEXT NOT NULL,

    port INTEGER NOT NULL DEFAULT 161
        CHECK (port > 0 AND port <= 65535),

    community_string TEXT,

    is_primary INTEGER NOT NULL DEFAULT 0
        CHECK (is_primary IN (0, 1)),

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_device_credentials_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_device_credentials_device_protocol
        UNIQUE (device_id, protocol_type)
);


-- ============================================================
-- 3. DEVICE INTERFACES
-- ============================================================

CREATE TABLE device_interfaces (
    device_id INTEGER NOT NULL,

    interface_index INTEGER NOT NULL
        CHECK (interface_index > 0),

    interface_name TEXT NOT NULL,

    mac_address TEXT,

    speed_bps INTEGER
        CHECK (
            speed_bps IS NULL
            OR speed_bps >= 0
        ),

    admin_status TEXT NOT NULL,

    oper_status TEXT NOT NULL,

    discovered_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (device_id, interface_index),

    CONSTRAINT fk_device_interfaces_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);


-- ============================================================
-- 4. OID CONFIGS
-- ============================================================

CREATE TABLE oid_configs (
    id INTEGER PRIMARY KEY,

    metric_scope TEXT NOT NULL,

    metric_type TEXT NOT NULL,

    oid_pattern TEXT NOT NULL,

    data_type TEXT NOT NULL,

    multiplier REAL NOT NULL DEFAULT 1,

    device_type TEXT,

    device_id INTEGER,

    interface_index INTEGER,

    is_active INTEGER NOT NULL DEFAULT 1
        CHECK (is_active IN (0, 1)),

    description TEXT,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
-- 5. METRICS
-- ============================================================

CREATE TABLE metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    device_id INTEGER NOT NULL,

    interface_index INTEGER,

    metrics TEXT NOT NULL,

    timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
-- 6. SYSLOGS
-- ============================================================

CREATE TABLE syslogs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    device_id INTEGER,

    ip_address TEXT NOT NULL,

    facility TEXT NOT NULL,

    severity TEXT NOT NULL,

    app_name TEXT,

    message TEXT NOT NULL,

    raw_log TEXT NOT NULL,

    timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_syslogs_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL
);


-- ============================================================
-- 7. SYNC QUEUE
-- ============================================================

CREATE TABLE sync_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    entity_type TEXT NOT NULL,

    entity_id TEXT,

    operation TEXT NOT NULL,

    payload TEXT NOT NULL,

    status TEXT NOT NULL DEFAULT 'PENDING',

    retry_count INTEGER NOT NULL DEFAULT 0
        CHECK (retry_count >= 0),

    last_error TEXT,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);