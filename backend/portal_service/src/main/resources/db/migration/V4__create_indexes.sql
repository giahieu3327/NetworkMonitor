CREATE INDEX idx_devices_status
ON devices(status);

CREATE INDEX idx_device_credentials_device_active
ON device_credentials(device_id, is_active);

CREATE INDEX idx_oid_configs_device_active
ON oid_configs(device_id, is_active);

CREATE INDEX idx_metrics_device_timestamp
ON metrics(device_id, timestamp DESC);

CREATE INDEX idx_metrics_timestamp
ON metrics(timestamp DESC);

CREATE UNIQUE INDEX idx_metric_aggregate_period
ON metric_aggregate(
    device_id,
    aggregation_window,
    period_start
);

CREATE INDEX idx_metric_aggregate_period_start
ON metric_aggregate(period_start DESC);

CREATE INDEX idx_threshold_rules_device_enabled
ON threshold_rules(device_id, is_enabled);

CREATE INDEX idx_incidents_status_created_at
ON incidents(status, created_at DESC);

CREATE INDEX idx_incidents_device
ON incidents(device_id);

CREATE INDEX idx_incidents_threshold_rule
ON incidents(threshold_rule_id);

CREATE INDEX idx_arp_mac_tables_ip
ON arp_mac_tables(ip_address);

CREATE INDEX idx_arp_mac_tables_mac
ON arp_mac_tables(mac_address);