-- ============================================================
-- FILE: V2__create_functions_and_triggers.sql
-- Mô tả: Function và trigger tự động cập nhật updated_at
-- ============================================================


-- ============================================================
-- FUNCTION
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


-- ============================================================
-- USERS
-- ============================================================

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- DEVICES
-- ============================================================

CREATE TRIGGER trg_devices_updated_at
BEFORE UPDATE ON devices
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- DEVICE CREDENTIALS
-- ============================================================

CREATE TRIGGER trg_device_credentials_updated_at
BEFORE UPDATE ON device_credentials
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- DEVICE INTERFACES
-- ============================================================

CREATE TRIGGER trg_device_interfaces_updated_at
BEFORE UPDATE ON device_interfaces
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- OID CONFIGS
-- ============================================================

CREATE TRIGGER trg_oid_configs_updated_at
BEFORE UPDATE ON oid_configs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- THRESHOLD RULES
-- ============================================================

CREATE TRIGGER trg_threshold_rules_updated_at
BEFORE UPDATE ON threshold_rules
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- SYSLOG RULES
-- ============================================================

CREATE TRIGGER trg_syslog_rules_updated_at
BEFORE UPDATE ON syslog_rules
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- INCIDENTS
-- ============================================================

CREATE TRIGGER trg_incidents_updated_at
BEFORE UPDATE ON incidents
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- NOTIFICATION CONFIGS
-- ============================================================

CREATE TRIGGER trg_notification_configs_updated_at
BEFORE UPDATE ON notification_configs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- TOPOLOGY MAPS
-- ============================================================

CREATE TRIGGER trg_topology_maps_updated_at
BEFORE UPDATE ON topology_maps
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();


-- ============================================================
-- ARP / MAC TABLES
-- ============================================================

CREATE TRIGGER trg_arp_mac_tables_updated_at
BEFORE UPDATE ON arp_mac_tables
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();