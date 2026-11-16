
-- =============================================
-- DEVICES
-- =============================================

ALTER TABLE devices
ADD CONSTRAINT chk_devices_status
CHECK (
    status IN ('UP', 'DOWN', 'UNKNOWN')
);

-- =============================================
-- DEVICE CREDENTIALS
-- =============================================

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_type
CHECK (
    credential_type = 'SNMP'
);

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_protocol
CHECK (
    protocol_type IN (
        'SNMPV1',
        'SNMPV2C',
        'SNMPV3'
    )
);

ALTER TABLE device_credentials
ADD CONSTRAINT chk_device_credentials_port
CHECK (
    port IS NULL
    OR port BETWEEN 1 AND 65535
);


-- =============================================
-- DEVICE INTERFACES
-- =============================================

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_index
CHECK (
    interface_index > 0
);

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_type
CHECK (
    interface_type IS NULL
    OR interface_type > 0
);

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_mtu
CHECK (
    mtu IS NULL
    OR mtu >= 0
);

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_speed
CHECK (
    speed_bps IS NULL
    OR speed_bps >= 0
);

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_admin_status
CHECK (
    admin_status IS NULL
    OR admin_status IN (
        'UP',
        'DOWN',
        'TESTING',
        'UNKNOWN'
    )
);

ALTER TABLE device_interfaces
ADD CONSTRAINT chk_device_interfaces_oper_status
CHECK (
    oper_status IS NULL
    OR oper_status IN (
        'UP',
        'DOWN',
        'TESTING',
        'UNKNOWN',
        'DORMANT',
        'NOT_PRESENT',
        'LOWER_LAYER_DOWN'
    )
);


-- =============================================
-- OID CONFIGS
-- =============================================

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_scope
CHECK (
    metric_scope IN (
        'DEVICE',
        'INTERFACE',
        'METRIC',
        'ARP'
    )
);

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_interface_scope
CHECK (
    interface_index IS NULL
    OR metric_scope IN (
        'INTERFACE',
        'METRIC'
    )
);

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_interface_index
CHECK (
    interface_index IS NULL
    OR interface_index > 0
);

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_multiplier
CHECK (
    multiplier > 0
);

ALTER TABLE oid_configs
ADD CONSTRAINT chk_oid_configs_kind
CHECK (oid_kind IN ('SINGLE', 'TABLE_COLUMN'));

-- =============================================
-- METRICS
-- =============================================

ALTER TABLE metrics
ADD CONSTRAINT chk_metrics_data_object
CHECK (
    jsonb_typeof(metric_data) = 'object'
);


-- =============================================
-- METRIC AGGREGATE
-- =============================================

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_window
CHECK (
    aggregation_window IN ('1H', '6H', '1D')
);

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_period
CHECK (
    period_end > period_start
);

ALTER TABLE metric_aggregate
ADD CONSTRAINT chk_metric_aggregate_data_object
CHECK (
    jsonb_typeof(aggregated_data) = 'object'
);


-- =============================================
-- THRESHOLD RULES
-- =============================================

ALTER TABLE threshold_rules
ADD CONSTRAINT chk_threshold_rules_occurrences
CHECK (
    consecutive_occurrences > 0
);

ALTER TABLE threshold_rules
ADD CONSTRAINT chk_threshold_rules_limits
CHECK (
    warning_limit IS NULL
    OR critical_limit IS NULL
    OR warning_limit < critical_limit
);

ALTER TABLE threshold_rules
ADD CONSTRAINT chk_threshold_rules_interface_index
CHECK (
    interface_index IS NULL
    OR interface_index > 0
);


-- =============================================
-- INCIDENTS
-- =============================================

ALTER TABLE incidents
ADD CONSTRAINT chk_incidents_severity
CHECK (
    severity IN (
        'INFO',
        'WARNING',
        'CRITICAL'
    )
);

ALTER TABLE incidents
ADD CONSTRAINT chk_incidents_status
CHECK (
    status IN (
        'OPEN',
        'ACKNOWLEDGED',
        'RESOLVED'
    )
);

ALTER TABLE incidents
ADD CONSTRAINT chk_incidents_resolved_at
CHECK (
    resolved_at IS NULL
    OR resolved_at >= created_at
);


-- =============================================
-- ARP MAC TABLES
-- =============================================

ALTER TABLE arp_mac_tables
ADD CONSTRAINT chk_arp_mac_tables_interface_index
CHECK (
    interface_index IS NULL
    OR interface_index > 0
);
