-- ============================================================
-- FILE: V3__create_indexes.sql
-- Database: SQLite
-- ============================================================


-- ============================================================
-- DEVICE CREDENTIALS
-- ============================================================

CREATE INDEX idx_device_credentials_device
ON device_credentials(device_id);


-- ============================================================
-- DEVICE INTERFACES
-- ============================================================

CREATE INDEX idx_device_interfaces_mac
ON device_interfaces(mac_address);


-- ============================================================
-- OID CONFIGS
-- ============================================================

CREATE INDEX idx_oid_configs_device
ON oid_configs(device_id);

CREATE INDEX idx_oid_configs_device_interface
ON oid_configs(
    device_id,
    interface_index
);

CREATE INDEX idx_oid_configs_metric_type
ON oid_configs(metric_type);


-- ============================================================
-- METRICS
-- ============================================================

CREATE INDEX idx_metrics_device_time
ON metrics(
    device_id,
    timestamp
);

CREATE INDEX idx_metrics_interface_time
ON metrics(
    device_id,
    interface_index,
    timestamp
);


-- ============================================================
-- SYSLOGS
-- ============================================================

CREATE INDEX idx_syslogs_device_time
ON syslogs(
    device_id,
    timestamp
);

CREATE INDEX idx_syslogs_severity_time
ON syslogs(
    severity,
    timestamp
);


-- ============================================================
-- SYNC QUEUE
-- ============================================================

CREATE INDEX idx_sync_queue_status
ON sync_queue(status);

CREATE INDEX idx_sync_queue_entity
ON sync_queue(
    entity_type,
    entity_id
);