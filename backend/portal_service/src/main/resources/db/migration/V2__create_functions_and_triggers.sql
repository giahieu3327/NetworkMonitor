
-- ============================================================
-- FILE: V2__create_functions_and_triggers.sql
-- Mô tả: Tạo function và trigger tự động cập nhật updated_at
-- ============================================================

-- ------------------------------------------------------------
-- FUNCTION: update_updated_at_column
-- Gán updated_at bằng thời điểm hiện tại trước khi UPDATE.
-- Dùng chung cho tất cả bảng có cột updated_at.
-- ------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION update_updated_at_column() IS
'Tự động cập nhật cột updated_at khi bản ghi được UPDATE.';


-- ------------------------------------------------------------
-- USERS
-- ------------------------------------------------------------

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- DEVICES
-- ------------------------------------------------------------

CREATE TRIGGER trg_devices_updated_at
BEFORE UPDATE ON devices
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- DEVICE_CREDENTIALS
-- ------------------------------------------------------------

CREATE TRIGGER trg_device_credentials_updated_at
BEFORE UPDATE ON device_credentials
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- DEVICE_INTERFACES
-- ------------------------------------------------------------

CREATE TRIGGER trg_device_interfaces_updated_at
BEFORE UPDATE ON device_interfaces
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- OID_CONFIGS
-- ------------------------------------------------------------

CREATE TRIGGER trg_oid_configs_updated_at
BEFORE UPDATE ON oid_configs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- THRESHOLD_RULES
-- ------------------------------------------------------------

CREATE TRIGGER trg_threshold_rules_updated_at
BEFORE UPDATE ON threshold_rules
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- SYSLOG_RULES
-- ------------------------------------------------------------

CREATE TRIGGER trg_syslog_rules_updated_at
BEFORE UPDATE ON syslog_rules
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- TOPOLOGY_MAPS
-- ------------------------------------------------------------

CREATE TRIGGER trg_topology_maps_updated_at
BEFORE UPDATE ON topology_maps
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ------------------------------------------------------------
-- ARP_MAC_TABLES
-- ------------------------------------------------------------

CREATE TRIGGER trg_arp_mac_tables_updated_at
BEFORE UPDATE ON arp_mac_tables
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
