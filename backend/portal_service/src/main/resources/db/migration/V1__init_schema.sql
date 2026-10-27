
-- ============================================================
-- FILE: V1__init_schema.sql
-- Mô tả: Tạo toàn bộ bảng và chú thích cho database NetworkMonitor
-- CSDL: PostgreSQL
-- ============================================================

-- ============================================================
-- 1. BẢNG USERS
-- Lưu thông tin người dùng ứng dụng.
-- Thông tin xác thực tài khoản được quản lý bởi Keycloak.
-- ============================================================

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

COMMENT ON TABLE users IS
'Thông tin người dùng NetworkMonitor; tài khoản và danh tính được quản lý bởi Keycloak.';

COMMENT ON COLUMN users.id IS
'ID người dùng dạng UUID, đồng bộ từ Keycloak.';

COMMENT ON COLUMN users.username IS
'Tên đăng nhập của người dùng.';

COMMENT ON COLUMN users.email IS
'Địa chỉ email người dùng, sử dụng để liên hệ và gửi thông báo.';

COMMENT ON COLUMN users.full_name IS
'Họ và tên đầy đủ của người dùng.';

COMMENT ON COLUMN users.phone_number IS
'Số điện thoại liên hệ của người dùng.';

COMMENT ON COLUMN users.role_name IS
'Vai trò ứng dụng, ví dụ ADMIN, MANAGER, GUEST_VIEWER.';

COMMENT ON COLUMN users.is_active IS
'TRUE nếu người dùng đang hoạt động; FALSE nếu tài khoản bị vô hiệu hóa.';

COMMENT ON COLUMN users.email_verified IS
'TRUE nếu email đã được xác minh.';

COMMENT ON COLUMN users.created_at IS
'Thời điểm tạo bản ghi người dùng.';

COMMENT ON COLUMN users.updated_at IS
'Thời điểm cập nhật gần nhất; được tự động cập nhật bằng trigger.';


-- ============================================================
-- 2. BẢNG DEVICES
-- Lưu danh sách thiết bị mạng cần giám sát.
-- ============================================================

CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    device_name VARCHAR(150) NOT NULL UNIQUE,
    ip_address INET NOT NULL UNIQUE,
    device_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE devices IS
'Danh sách thiết bị mạng được NetworkMonitor quản lý và giám sát.';

COMMENT ON COLUMN devices.id IS
'ID tự tăng của thiết bị trong PostgreSQL.';

COMMENT ON COLUMN devices.device_name IS
'Tên định danh của thiết bị mạng.';

COMMENT ON COLUMN devices.ip_address IS
'Địa chỉ IPv4 hoặc IPv6 của thiết bị, sử dụng kiểu INET của PostgreSQL.';

COMMENT ON COLUMN devices.device_type IS
'Loại thiết bị, ví dụ ROUTER, SWITCH, FIREWALL hoặc SERVER.';

COMMENT ON COLUMN devices.status IS
'Trạng thái thiết bị: UP, DOWN hoặc UNKNOWN.';

COMMENT ON COLUMN devices.created_at IS
'Thời điểm thêm thiết bị vào hệ thống.';

COMMENT ON COLUMN devices.updated_at IS
'Thời điểm cập nhật thông tin thiết bị gần nhất.';


-- ============================================================
-- 3. BẢNG DEVICE_CREDENTIALS
-- Lưu thông tin cấu hình truy cập và nhận log của thiết bị.
-- Không nên lưu credential dạng văn bản thuần nếu có thể tránh.
-- ============================================================

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

COMMENT ON TABLE device_credentials IS
'Cấu hình truy cập thiết bị phục vụ SNMP hoặc tiếp nhận Syslog.';

COMMENT ON COLUMN device_credentials.id IS
'ID tự tăng của cấu hình credential.';

COMMENT ON COLUMN device_credentials.device_id IS
'ID thiết bị sở hữu cấu hình; tham chiếu devices.id.';

COMMENT ON COLUMN device_credentials.credential_name IS
'Tên dễ nhận biết của cấu hình truy cập.';

COMMENT ON COLUMN device_credentials.credential_type IS
'Loại cấu hình: SNMP hoặc SYSLOG.';

COMMENT ON COLUMN device_credentials.protocol_type IS
'Giao thức sử dụng: SNMPV1, SNMPV2C, SNMPV3, UDP, TCP hoặc TLS.';

COMMENT ON COLUMN device_credentials.community_string IS
'Thông tin community hoặc dữ liệu xác thực liên quan; cần bảo vệ hoặc mã hóa khi lưu.';

COMMENT ON COLUMN device_credentials.port IS
'Cổng giao tiếp được cấu hình, ví dụ cổng SNMP hoặc cổng nhận Syslog.';

COMMENT ON COLUMN device_credentials.is_active IS
'TRUE nếu cấu hình đang được sử dụng.';

COMMENT ON COLUMN device_credentials.created_at IS
'Thời điểm tạo cấu hình.';

COMMENT ON COLUMN device_credentials.updated_at IS
'Thời điểm cập nhật cấu hình gần nhất.';


-- ============================================================
-- 4. BẢNG DEVICE_INTERFACES
-- Lưu các interface của từng thiết bị mạng.
-- Khóa chính gồm device_id và interface_index.
-- ============================================================

CREATE TABLE device_interfaces (
    device_id BIGINT NOT NULL,
    interface_index INTEGER NOT NULL,
    interface_name VARCHAR(150) NOT NULL,
    speed_bps BIGINT,
    admin_status VARCHAR(30),
    oper_status VARCHAR(30),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_device_interfaces
        PRIMARY KEY (device_id, interface_index),

    CONSTRAINT fk_device_interfaces_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);

COMMENT ON TABLE device_interfaces IS
'Danh sách cổng/interface mạng thuộc từng thiết bị.';

COMMENT ON COLUMN device_interfaces.device_id IS
'ID thiết bị sở hữu interface.';

COMMENT ON COLUMN device_interfaces.interface_index IS
'Chỉ số interface, thường tương ứng với ifIndex trong SNMP.';

COMMENT ON COLUMN device_interfaces.interface_name IS
'Tên interface, ví dụ GigabitEthernet1/0/1 hoặc eth0.';

COMMENT ON COLUMN device_interfaces.speed_bps IS
'Tốc độ danh nghĩa của interface, tính bằng bit mỗi giây.';

COMMENT ON COLUMN device_interfaces.admin_status IS
'Tình trạng quản trị của interface, ví dụ up, down hoặc testing.';

COMMENT ON COLUMN device_interfaces.oper_status IS
'Tình trạng hoạt động thực tế của interface, ví dụ up hoặc down.';

COMMENT ON COLUMN device_interfaces.updated_at IS
'Thời điểm thông tin interface được cập nhật gần nhất.';


-- ============================================================
-- 5. BẢNG OID_CONFIGS
-- Lưu cấu hình OID dùng để thu thập chỉ số SNMP.
-- INTERFACE dùng interface_index; DEVICE không gắn interface.
-- ============================================================

CREATE TABLE oid_configs (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    interface_index INTEGER,
    metric_scope VARCHAR(20) NOT NULL,
    metric_type VARCHAR(100) NOT NULL,
    oid_pattern TEXT NOT NULL,
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

COMMENT ON TABLE oid_configs IS
'Cấu hình OID và cách chuyển đổi giá trị khi thu thập dữ liệu SNMP.';

COMMENT ON COLUMN oid_configs.id IS
'ID tự tăng của cấu hình OID.';

COMMENT ON COLUMN oid_configs.device_id IS
'ID thiết bị áp dụng cấu hình OID.';

COMMENT ON COLUMN oid_configs.interface_index IS
'Chỉ số interface nếu cấu hình áp dụng cho một interface cụ thể; NULL nếu áp dụng cấp thiết bị.';

COMMENT ON COLUMN oid_configs.metric_scope IS
'Phạm vi chỉ số: DEVICE hoặc INTERFACE.';

COMMENT ON COLUMN oid_configs.metric_type IS
'Tên chỉ số cần thu thập, ví dụ CPU_USAGE, MEMORY_USAGE, IN_BPS hoặc OUT_BPS.';

COMMENT ON COLUMN oid_configs.oid_pattern IS
'OID SNMP hoặc mẫu OID cần truy vấn; có thể chứa phần chỉ số interface.';

COMMENT ON COLUMN oid_configs.data_type IS
'Kiểu dữ liệu của giá trị SNMP, ví dụ INTEGER, GAUGE, COUNTER hoặc STRING.';

COMMENT ON COLUMN oid_configs.multiplier IS
'Hệ số nhân để quy đổi giá trị SNMP sang đơn vị cần sử dụng.';

COMMENT ON COLUMN oid_configs.is_active IS
'TRUE nếu cấu hình OID được bật để thu thập dữ liệu.';

COMMENT ON COLUMN oid_configs.created_at IS
'Thời điểm tạo cấu hình OID.';

COMMENT ON COLUMN oid_configs.updated_at IS
'Thời điểm cập nhật cấu hình OID gần nhất.';


-- ============================================================
-- 6. BẢNG METRICS
-- Lưu dữ liệu giám sát thô theo từng chu kỳ thu thập.
-- Mỗi bản ghi chứa một JSON object gồm nhiều chỉ số thiết bị.
-- ============================================================

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

COMMENT ON TABLE metrics IS
'Dữ liệu giám sát thô của thiết bị; mỗi bản ghi đại diện cho một chu kỳ thu thập.';

COMMENT ON COLUMN metrics.id IS
'ID tự tăng của bản ghi metrics.';

COMMENT ON COLUMN metrics.device_id IS
'ID thiết bị được thu thập chỉ số.';

COMMENT ON COLUMN metrics.metric_data IS
'Các chỉ số thu thập được lưu trong JSON object, ví dụ CPU, RAM, độ trễ và băng thông.';

COMMENT ON COLUMN metrics.timestamp IS
'Thời điểm thu thập dữ liệu metrics.';


-- ============================================================
-- 7. BẢNG METRIC_AGGREGATE
-- Lưu dữ liệu metrics đã tổng hợp theo khoảng thời gian.
-- ============================================================

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

COMMENT ON TABLE metric_aggregate IS
'Dữ liệu metrics tổng hợp theo các khoảng 1 giờ, 6 giờ hoặc 1 ngày.';

COMMENT ON COLUMN metric_aggregate.id IS
'ID tự tăng của bản ghi tổng hợp.';

COMMENT ON COLUMN metric_aggregate.device_id IS
'ID thiết bị có dữ liệu được tổng hợp.';

COMMENT ON COLUMN metric_aggregate.aggregation_window IS
'Độ dài khoảng tổng hợp: 1H, 6H hoặc 1D.';

COMMENT ON COLUMN metric_aggregate.period_start IS
'Thời điểm bắt đầu khoảng tổng hợp.';

COMMENT ON COLUMN metric_aggregate.period_end IS
'Thời điểm kết thúc khoảng tổng hợp.';

COMMENT ON COLUMN metric_aggregate.aggregated_data IS
'Dữ liệu tổng hợp JSON object, có thể chứa min, max, avg và các thông tin thống kê khác.';


-- ============================================================
-- 8. BẢNG SYSLOGS
-- Lưu các thông điệp Syslog nhận được từ thiết bị.
-- ============================================================

CREATE TABLE syslogs (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT,
    ip_address INET NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    message TEXT NOT NULL,
    raw_log TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_syslogs_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE SET NULL
);

COMMENT ON TABLE syslogs IS
'Lưu thông điệp Syslog nhận từ thiết bị để tra cứu, phân tích và tạo incident.';

COMMENT ON COLUMN syslogs.id IS
'ID tự tăng của bản ghi Syslog.';

COMMENT ON COLUMN syslogs.device_id IS
'ID thiết bị đã được nhận diện; NULL nếu thiết bị chưa được nhận diện hoặc đã bị xóa.';

COMMENT ON COLUMN syslogs.ip_address IS
'Địa chỉ IP nguồn của thông điệp Syslog.';

COMMENT ON COLUMN syslogs.severity IS
'Mức độ nghiêm trọng gốc của Syslog theo syslog severity.';

COMMENT ON COLUMN syslogs.message IS
'Nội dung thông điệp Syslog đã được phân tích.';

COMMENT ON COLUMN syslogs.raw_log IS
'Nội dung Syslog nguyên bản để phục vụ kiểm tra và phân tích khi cần.';

COMMENT ON COLUMN syslogs.received_at IS
'Thời điểm hệ thống nhận thông điệp Syslog.';


-- ============================================================
-- 9. BẢNG THRESHOLD_RULES
-- Định nghĩa ngưỡng cảnh báo dựa trên metrics.
-- ============================================================

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

COMMENT ON TABLE threshold_rules IS
'Các quy tắc phát hiện bất thường dựa trên ngưỡng chỉ số metrics.';

COMMENT ON COLUMN threshold_rules.id IS
'ID tự tăng của quy tắc ngưỡng.';

COMMENT ON COLUMN threshold_rules.device_id IS
'ID thiết bị áp dụng quy tắc.';

COMMENT ON COLUMN threshold_rules.interface_index IS
'Interface áp dụng quy tắc; NULL nếu áp dụng cho toàn thiết bị.';

COMMENT ON COLUMN threshold_rules.rule_name IS
'Tên mô tả của quy tắc cảnh báo.';

COMMENT ON COLUMN threshold_rules.metric_type IS
'Loại chỉ số cần kiểm tra, ví dụ CPU_USAGE hoặc IN_BPS.';

COMMENT ON COLUMN threshold_rules.warning_limit IS
'Ngưỡng cảnh báo WARNING; cách so sánh phụ thuộc loại metrics.';

COMMENT ON COLUMN threshold_rules.critical_limit IS
'Ngưỡng cảnh báo CRITICAL; cách so sánh phụ thuộc loại metrics.';

COMMENT ON COLUMN threshold_rules.consecutive_occurrences IS
'Số lần liên tiếp vượt ngưỡng trước khi kích hoạt cảnh báo.';

COMMENT ON COLUMN threshold_rules.is_enabled IS
'TRUE nếu quy tắc đang được sử dụng.';

COMMENT ON COLUMN threshold_rules.created_at IS
'Thời điểm tạo quy tắc.';

COMMENT ON COLUMN threshold_rules.updated_at IS
'Thời điểm cập nhật quy tắc gần nhất.';


-- ============================================================
-- 10. BẢNG SYSLOG_RULES
-- Định nghĩa quy tắc nhận diện sự kiện từ Syslog.
-- device_id NULL nghĩa là quy tắc áp dụng toàn hệ thống.
-- ============================================================

CREATE TABLE syslog_rules (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT,
    rule_name VARCHAR(150) NOT NULL,
    match_pattern TEXT NOT NULL,
    match_severity VARCHAR(20),
    assign_severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_syslog_rules_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE
);

COMMENT ON TABLE syslog_rules IS
'Các quy tắc lọc và phân loại Syslog để xác định sự kiện cần cảnh báo.';

COMMENT ON COLUMN syslog_rules.id IS
'ID tự tăng của quy tắc Syslog.';

COMMENT ON COLUMN syslog_rules.device_id IS
'ID thiết bị áp dụng quy tắc; NULL nghĩa là áp dụng toàn hệ thống.';

COMMENT ON COLUMN syslog_rules.rule_name IS
'Tên mô tả của quy tắc Syslog.';

COMMENT ON COLUMN syslog_rules.match_pattern IS
'Chuỗi hoặc biểu thức mẫu dùng để đối chiếu nội dung Syslog.';

COMMENT ON COLUMN syslog_rules.match_severity IS
'Mức severity gốc cần lọc; NULL nếu không lọc theo severity.';

COMMENT ON COLUMN syslog_rules.assign_severity IS
'Mức severity của incident được tạo ra: INFO, WARNING hoặc CRITICAL.';

COMMENT ON COLUMN syslog_rules.is_enabled IS
'TRUE nếu quy tắc Syslog đang được bật.';

COMMENT ON COLUMN syslog_rules.created_at IS
'Thời điểm tạo quy tắc Syslog.';

COMMENT ON COLUMN syslog_rules.updated_at IS
'Thời điểm cập nhật quy tắc Syslog gần nhất.';


-- ============================================================
-- 11. BẢNG INCIDENTS
-- Lưu các sự cố/cảnh báo được phát hiện trong hệ thống.
-- ============================================================

CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT,
    threshold_rule_id BIGINT,
    syslog_rule_id BIGINT,
    syslog_id BIGINT,
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
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_syslog_rule
        FOREIGN KEY (syslog_rule_id)
        REFERENCES syslog_rules(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_incidents_syslog
        FOREIGN KEY (syslog_id)
        REFERENCES syslogs(id)
        ON DELETE SET NULL
);

COMMENT ON TABLE incidents IS
'Lưu các sự cố được phát hiện từ metrics hoặc Syslog và theo dõi quá trình xử lý.';

COMMENT ON COLUMN incidents.id IS
'ID tự tăng của incident.';

COMMENT ON COLUMN incidents.device_id IS
'ID thiết bị liên quan đến sự cố; NULL nếu không xác định được hoặc thiết bị đã bị xóa.';

COMMENT ON COLUMN incidents.threshold_rule_id IS
'Quy tắc ngưỡng đã kích hoạt sự cố; NULL nếu không phát sinh từ threshold rule.';

COMMENT ON COLUMN incidents.syslog_rule_id IS
'Quy tắc Syslog đã kích hoạt sự cố; NULL nếu không phát sinh từ Syslog rule.';

COMMENT ON COLUMN incidents.syslog_id IS
'Bản ghi Syslog nguồn liên quan đến sự cố, nếu có.';

COMMENT ON COLUMN incidents.severity IS
'Mức độ của incident: INFO, WARNING hoặc CRITICAL.';

COMMENT ON COLUMN incidents.title IS
'Tiêu đề ngắn mô tả sự cố.';

COMMENT ON COLUMN incidents.description IS
'Chi tiết nguyên nhân, giá trị metrics hoặc thông tin sự kiện liên quan.';

COMMENT ON COLUMN incidents.status IS
'Trạng thái xử lý: OPEN, ACKNOWLEDGED hoặc RESOLVED.';

COMMENT ON COLUMN incidents.camunda_process_id IS
'ID tiến trình Camunda liên quan đến quy trình xử lý sự cố, nếu có.';

COMMENT ON COLUMN incidents.created_at IS
'Thời điểm phát hiện và tạo incident.';

COMMENT ON COLUMN incidents.resolved_at IS
'Thời điểm sự cố được đánh dấu đã xử lý; NULL nếu chưa giải quyết.';


-- ============================================================
-- 12. BẢNG TOPOLOGY_MAPS
-- Lưu sơ đồ topology mạng dưới dạng JSONB.
-- ============================================================

CREATE TABLE topology_maps (
    id BIGSERIAL PRIMARY KEY,
    map_name VARCHAR(150) NOT NULL,
    nodes_data JSONB NOT NULL DEFAULT '[]'::JSONB,
    edges_data JSONB NOT NULL DEFAULT '[]'::JSONB,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE topology_maps IS
'Lưu sơ đồ topology mạng gồm danh sách node và kết nối giữa các node.';

COMMENT ON COLUMN topology_maps.id IS
'ID tự tăng của sơ đồ topology.';

COMMENT ON COLUMN topology_maps.map_name IS
'Tên sơ đồ topology.';

COMMENT ON COLUMN topology_maps.nodes_data IS
'Mảng JSONB chứa thông tin các node, ví dụ thiết bị và vị trí hiển thị.';

COMMENT ON COLUMN topology_maps.edges_data IS
'Mảng JSONB chứa thông tin các kết nối giữa node trong sơ đồ.';

COMMENT ON COLUMN topology_maps.is_default IS
'TRUE nếu đây là sơ đồ mặc định của hệ thống.';

COMMENT ON COLUMN topology_maps.created_at IS
'Thời điểm tạo sơ đồ.';

COMMENT ON COLUMN topology_maps.updated_at IS
'Thời điểm cập nhật sơ đồ gần nhất.';


-- ============================================================
-- 13. BẢNG ARP_MAC_TABLES
-- Lưu ánh xạ địa chỉ IP và MAC quan sát được trong mạng.
-- ============================================================

CREATE TABLE arp_mac_tables (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    interface_index INTEGER,
    ip_address INET NOT NULL,
    mac_address MACADDR NOT NULL,
    vlan_id INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_arp_mac_tables_device
        FOREIGN KEY (device_id)
        REFERENCES devices(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_arp_mac_tables_interface
        FOREIGN KEY (device_id, interface_index)
        REFERENCES device_interfaces(device_id, interface_index)
        ON DELETE SET NULL (interface_index)
);

COMMENT ON TABLE arp_mac_tables IS
'Lưu thông tin ánh xạ IP-MAC thu thập từ thiết bị mạng để tra cứu vị trí thiết bị.';

COMMENT ON COLUMN arp_mac_tables.id IS
'ID tự tăng của bản ghi ánh xạ IP-MAC.';

COMMENT ON COLUMN arp_mac_tables.device_id IS
'ID thiết bị mạng đã cung cấp hoặc quan sát thông tin IP-MAC.';

COMMENT ON COLUMN arp_mac_tables.interface_index IS
'Interface liên quan đến bản ghi; NULL nếu chưa xác định được interface.';

COMMENT ON COLUMN arp_mac_tables.ip_address IS
'Địa chỉ IP được ánh xạ.';

COMMENT ON COLUMN arp_mac_tables.mac_address IS
'Địa chỉ MAC tương ứng với IP.';

COMMENT ON COLUMN arp_mac_tables.vlan_id IS
'ID VLAN liên quan nếu thiết bị cung cấp thông tin VLAN.';

COMMENT ON COLUMN arp_mac_tables.updated_at IS
'Thời điểm gần nhất bản ghi IP-MAC được cập nhật.';


-- ============================================================
-- 14. BẢNG AUDIT_LOGS
-- Lưu lịch sử thao tác của người dùng và các hoạt động hệ thống.
-- ============================================================

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36),
    action VARCHAR(100) NOT NULL,
    module VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    details JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);

COMMENT ON TABLE audit_logs IS
'Lịch sử thao tác phục vụ kiểm tra, truy vết và kiểm toán hoạt động hệ thống.';

COMMENT ON COLUMN audit_logs.id IS
'ID tự tăng của bản ghi audit log.';

COMMENT ON COLUMN audit_logs.user_id IS
'ID người dùng thực hiện thao tác; NULL nếu là hoạt động hệ thống hoặc người dùng đã bị xóa.';

COMMENT ON COLUMN audit_logs.action IS
'Tên hành động, ví dụ CREATE, UPDATE, DELETE, LOGIN hoặc ACKNOWLEDGE.';

COMMENT ON COLUMN audit_logs.module IS
'Phân hệ thực hiện thao tác, ví dụ DEVICES, USERS, INCIDENTS hoặc TOPOLOGY.';

COMMENT ON COLUMN audit_logs.entity_id IS
'ID của đối tượng bị tác động; lưu dạng chuỗi để hỗ trợ nhiều loại ID.';

COMMENT ON COLUMN audit_logs.details IS
'Thông tin bổ sung dưới dạng JSONB, ví dụ dữ liệu trước/sau khi thay đổi.';

COMMENT ON COLUMN audit_logs.timestamp IS
'Thời điểm ghi nhận thao tác.';
