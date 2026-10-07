-- ============================================================
-- FILE: V2__create_functions_and_triggers.sql
-- Database: SQLite
--
-- SQLite không có PL/pgSQL function.
-- Sử dụng trigger để tự động cập nhật updated_at
-- khi record được UPDATE.
-- ============================================================


-- ============================================================
-- 1. DEVICES
-- ============================================================

CREATE TRIGGER trg_devices_updated_at
AFTER UPDATE ON devices
FOR EACH ROW
BEGIN
    UPDATE devices
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.id;
END;


-- ============================================================
-- 2. DEVICE CREDENTIALS
-- ============================================================

CREATE TRIGGER trg_device_credentials_updated_at
AFTER UPDATE ON device_credentials
FOR EACH ROW
BEGIN
    UPDATE device_credentials
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.id;
END;


-- ============================================================
-- 3. DEVICE INTERFACES
-- ============================================================

CREATE TRIGGER trg_device_interfaces_updated_at
AFTER UPDATE ON device_interfaces
FOR EACH ROW
BEGIN
    UPDATE device_interfaces
    SET updated_at = CURRENT_TIMESTAMP
    WHERE device_id = OLD.device_id
      AND interface_index = OLD.interface_index;
END;


-- ============================================================
-- 4. OID CONFIGS
-- ============================================================

CREATE TRIGGER trg_oid_configs_updated_at
AFTER UPDATE ON oid_configs
FOR EACH ROW
BEGIN
    UPDATE oid_configs
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.id;
END;


-- ============================================================
-- 5. SYNC QUEUE
-- ============================================================

CREATE TRIGGER trg_sync_queue_updated_at
AFTER UPDATE ON sync_queue
FOR EACH ROW
BEGIN
    UPDATE sync_queue
    SET updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.id;
END;