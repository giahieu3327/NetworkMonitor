-- ============================================================
-- FILE: schema.sql (PostgreSQL DDL)
-- Mô tả: Cơ sở dữ liệu hệ thống giám sát hạ tầng mạng (NMS)
-- Định hướng: CHỈ ĐỌC & GIÁM SÁT (READ-ONLY MONITORING & ALERTING)
-- Cấu trúc Metrics: DYNAMIC JSONB (Linh hoạt theo OID)
-- Tích hợp: Syslog & SNMP Threshold Event Automation (Camunda)
-- ============================================================

-- ============================================================
-- PHẦN 1: TẠO BẢNG (CREATE TABLES)
-- ============================================================

-- ------------------------------------------------------------
-- NHÓM 1: QUẢN LÝ NGƯỜI DÙNG & TÀI KHOẢN (LOCAL / KEYCLOAK)
-- ------------------------------------------------------------

-- 1.1. Lưu trữ thông tin tài khoản người dùng được đồng bộ từ Keycloak Profile
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY, -- Subject Identifier (UUID) từ Keycloak
    username VARCHAR(50) NOT NULL UNIQUE, -- Tên đăng nhập
    email VARCHAR(100) NOT NULL UNIQUE, -- Địa chỉ email
    full_name VARCHAR(100) NOT NULL, -- Họ và tên đầy đủ
    phone_number VARCHAR(20), -- Số điện thoại liên hệ
    is_active BOOLEAN DEFAULT TRUE, -- Trạng thái tài khoản (TRUE: Hoạt động, FALSE: Khóa mềm giữ lịch sử)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo tài khoản
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật thông tin gần nhất
);


-- ------------------------------------------------------------
-- NHÓM 2: QUẢN LÝ THIẾT BỊ, XÁC THỰC & CỔNG GIAO TIẾP
-- ------------------------------------------------------------

-- 2.1. Danh sách các thiết bị phần cứng trong hạ tầng mạng cần giám sát
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh thiết bị
    device_name VARCHAR(100) NOT NULL, -- Tên gợi nhớ của thiết bị (Hostname)
    ip_address VARCHAR(45) NOT NULL UNIQUE, -- Địa chỉ IP quản lý (IPv4 hoặc IPv6)
    device_type VARCHAR(30) NOT NULL, -- Phân loại thiết bị (CORE_SWITCH, DISTRIBUTION_SWITCH, FIREWALL, ROUTER)
    model VARCHAR(100), -- Dòng thiết bị/model phần cứng (VD: C9300-24T, FortiGate-100F...)
    firmware_version VARCHAR(50), -- Phiên bản Firmware/OS (VD: v7.2.13, 8.4.0, 16.9.2) - Dùng tra cứu
    status VARCHAR(20) DEFAULT 'UP', -- Trạng thái kết nối của thiết bị (UP, DOWN, WARNING)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm thêm thiết bị vào hệ thống
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật thông tin thiết bị
);

-- 2.2. Thông tin cấu hình kết nối SNMP Read-Only (v1/v2c) và tiếp nhận Syslog cho từng thiết bị
CREATE TABLE device_credentials (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh thông số xác thực
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, -- Liên kết tới thiết bị tương ứng
    protocol_type VARCHAR(20) NOT NULL, -- Giao thức kết nối (SNMP_V1, SNMP_V2C, SYSLOG)
    port INT NOT NULL DEFAULT 161, -- Cổng kết nối (VD: 161 cho SNMP, 514 cho Syslog)
    community_string VARCHAR(100), -- Chuỗi Community String Read-Only dùng cho SNMP v1/v2c
    is_primary BOOLEAN DEFAULT TRUE, -- Cờ đánh dấu cấu hình ưu tiên thu thập chính
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo cấu hình
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật cấu hình
);

-- 2.3. Danh sách các cổng/giao diện mạng trên từng thiết bị (Tự động phát hiện qua SNMP Auto-Discovery)
CREATE TABLE device_interfaces (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh cổng
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, -- Liên kết tới thiết bị sở hữu cổng
    interface_index INT NOT NULL, -- Chỉ số cổng trên thiết bị (ifIndex trong SNMP MIB-II)
    interface_name VARCHAR(50) NOT NULL, -- Tên giao diện (VD: GigabitEthernet1/0/1, port1, wan1...)
    mac_address VARCHAR(17), -- Địa chỉ MAC vật lý của cổng
    speed_bps BIGINT DEFAULT 1000000000, -- Tốc độ thiết kế của cổng tính bằng Bps (Mặc định 1 Gbps)
    admin_status VARCHAR(10) DEFAULT 'UP', -- Trạng thái quản lý do admin bật/tắt trên thiết bị (UP, DOWN)
    oper_status VARCHAR(10) DEFAULT 'UP', -- Trạng thái hoạt động thực tế của đường truyền (UP, DOWN)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cổng được phát hiện/thêm vào hệ thống
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật trạng thái cổng gần nhất
);


-- ------------------------------------------------------------
-- NHÓM 3: CẤU HÌNH OID & DỮ LIỆU GIÁM SÁT REAL-TIME (TIME-SERIES)
-- ------------------------------------------------------------

-- 3.1. Bảng mẫu và cấu hình chi tiết chuỗi OID SNMP để Poller Worker thu thập dữ liệu
CREATE TABLE oid_configs (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh cấu hình OID
    metric_scope VARCHAR(20) NOT NULL DEFAULT 'DEVICE', -- Phạm vi thu thập: 'DEVICE' (Toàn thiết bị) hoặc 'INTERFACE' (Theo từng cổng)
    metric_type VARCHAR(30) NOT NULL, -- Tên loại chỉ số chuẩn hóa (VD: CPU, RAM, TEMPERATURE, ACTIVE_SESSIONS, BANDWIDTH_IN, BANDWIDTH_OUT...)
    oid_pattern VARCHAR(255) NOT NULL, -- Chuỗi OID SNMP (VD: '1.3.6.1.4.1.9.9.109.1.1.1.1.5.1' hoặc '1.3.6.1.2.1.31.1.1.1.6.{ifIndex}')
    data_type VARCHAR(20) DEFAULT 'INTEGER', -- Kiểu dữ liệu SNMP trả về (INTEGER, GAUGE32, COUNTER32, COUNTER64, STRING, FLOAT)
    multiplier DOUBLE PRECISION DEFAULT 1.0, -- Hệ số nhân xử lý dữ liệu thô (VD: 0.1 nếu thiết bị trả về 450 để thành 45.0°C)
    device_type VARCHAR(30), -- Mẫu áp dụng theo loại thiết bị (NULL = Template chung; 'FIREWALL', 'CORE_SWITCH'...)
    device_id BIGINT REFERENCES devices(id) ON DELETE CASCADE, -- NULL = Template chung; NOT NULL = OID tùy chỉnh riêng cho đúng 1 thiết bị
    interface_id BIGINT REFERENCES device_interfaces(id) ON DELETE CASCADE, -- NULL = Metric thiết bị; NOT NULL = OID gắn riêng cho 1 cổng
    is_active BOOLEAN DEFAULT TRUE, -- Trạng thái thu thập (TRUE: Đang lấy dữ liệu, FALSE: Bỏ qua)
    description TEXT, -- Mô tả chi tiết ý nghĩa của chuỗi OID
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo cấu hình OID
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật cấu hình OID
);

-- 3.2. Lưu trữ toàn bộ chỉ số đo được của thiết bị dưới dạng JSON động (VD: {"cpu": 45.5, "ram": 60, "temp": 38, "sessions": 120})
CREATE TABLE device_metrics (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính bản ghi metric
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, -- Liên kết tới thiết bị được đo
    metrics JSONB NOT NULL, -- Dữ liệu Key-Value linh hoạt chứa mọi chỉ số thu thập được từ SNMP OID
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL -- Mốc thời gian thu thập dữ liệu
);

-- 3.3. Lưu trữ toàn bộ chỉ số đo được của từng cổng mạng dưới dạng JSON động (VD: {"bytes_in": 10240, "bytes_out": 20480, "errors_in": 0})
CREATE TABLE interface_metrics (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính bản ghi metric cổng
    interface_id BIGINT NOT NULL REFERENCES device_interfaces(id) ON DELETE CASCADE, -- Liên kết tới cổng mạng tương ứng
    metrics JSONB NOT NULL, -- Dữ liệu Key-Value linh hoạt chứa chỉ số băng thông/gói tin của cổng
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL -- Mốc thời gian thu thập dữ liệu
);

-- 3.4. Nhật ký sự kiện Syslog đẩy trực tiếp thụ động từ thiết bị mạng về tập trung (UDP 514)
CREATE TABLE syslogs (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính bản ghi log
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL, -- Liên kết thiết bị (Mở rộng từ IP nếu trùng khớp)
    ip_address VARCHAR(45) NOT NULL, -- Địa chỉ IP nguồn gửi Syslog
    facility VARCHAR(20), -- Phân loại nguồn phát sinh log (auth, cron, daemon, kernel, local0-7...)
    severity VARCHAR(20) NOT NULL, -- Mức độ nghiêm trọng (EMERGENCY, ALERT, CRITICAL, ERROR, WARNING, NOTICE, INFORMATIONAL, DEBUG)
    app_name VARCHAR(50), -- Tên tiến trình/ứng dụng tạo log trên thiết bị
    message TEXT NOT NULL, -- Nội dung thông điệp log đã phân tích (Parsed message)
    raw_log TEXT, -- Chuỗi log thô nguyên bản nhận qua gói tin UDP
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL -- Mốc thời gian ghi nhận log
);


-- ------------------------------------------------------------
-- NHÓM 4: CẤU HÌNH QUY TẮC CẢNH BÁO (THRESHOLD & SYSLOG RULES)
-- ------------------------------------------------------------

-- 4.1. Quy tắc ngưỡng cảnh báo theo metric_type và cơ chế chống cảnh báo ảo (Alert Flapping Prevention)
CREATE TABLE threshold_rules (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh luật cảnh báo
    rule_name VARCHAR(100) NOT NULL, -- Tên hiển thị của luật (VD: 'Cảnh báo CPU Cao', 'Cảnh báo Ngẽn Băng Thông')
    metric_type VARCHAR(30) NOT NULL, -- Loại chỉ số áp dụng đồng bộ với key trong JSON metrics (CPU, RAM, BANDWIDTH...)
    
    warning_limit DOUBLE PRECISION NOT NULL, -- Ngưỡng chạm mức Cảnh báo (Warning)
    critical_limit DOUBLE PRECISION NOT NULL, -- Ngưỡng chạm mức Nguy hiểm (Critical)
    
    -- BỘ LỌC CHỐNG CẢNH BÁO ẢO (ALERT FLAPPING PREVENTION)
    consecutive_occurrences INT DEFAULT 3, -- Số lần vi phạm liên tiếp bắt buộc trước khi bắn cảnh báo (Mặc định 3 lần)
    duration_seconds INT DEFAULT 300,      -- Thời gian vi phạm liên tục tối thiểu tính bằng giây (Mặc định 300s = 5 phút)
    
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL, -- NULL = Áp dụng cho mọi thiết bị; NOT NULL = Áp dụng riêng 1 thiết bị
    interface_id BIGINT REFERENCES device_interfaces(id) ON DELETE SET NULL, -- NULL = Metric thiết bị; NOT NULL = Áp dụng riêng 1 cổng mạng
    
    is_enabled BOOLEAN DEFAULT TRUE, -- Trạng thái kích hoạt luật (TRUE: Bật, FALSE: Tắt)
    created_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL, -- Người tạo luật
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo luật
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật luật
);

-- 4.2. Quy tắc lọc Syslog nguy hiểm tự động kích hoạt Incident & Camunda Workflow
CREATE TABLE syslog_rules (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh luật Syslog
    rule_name VARCHAR(100) NOT NULL, -- Tên hiển thị luật (VD: 'Bắt Log Link Down', 'Bắt Log Lỗi Nguồn Hardware')
    match_severity VARCHAR(20), -- Bắt theo mức độ log (EMERGENCY, ALERT, CRITICAL, ERROR...). NULL = Bỏ qua check severity
    match_pattern VARCHAR(255), -- Mẫu từ khóa hoặc Regex trong nội dung message (VD: '%LINK-3-UPDOWN', 'Fan failure', 'Authentication failed')
    
    assign_severity VARCHAR(20) NOT NULL DEFAULT 'CRITICAL', -- Mức độ gán cho Incident được tạo (WARNING, CRITICAL, DOWN)
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL, -- NULL = Áp dụng mọi thiết bị; NOT NULL = Áp dụng riêng 1 thiết bị
    
    is_enabled BOOLEAN DEFAULT TRUE, -- Trạng thái kích hoạt luật (TRUE: Bật, FALSE: Tắt)
    created_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL, -- Người tạo luật
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo luật
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật luật
);


-- ------------------------------------------------------------
-- NHÓM 5: QUẢN LÝ INCIDENT (SỰ CỐ) & CẤU HÌNH KÊNH THÔNG BÁO
-- ------------------------------------------------------------

-- 5.1. Quản lý các sự cố cảnh báo từ SNMP Thresholds hoặc Syslog Events (Liên kết Camunda Engine)
CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính định danh sự cố
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, -- Thiết bị xảy ra sự cố
    
    -- NGUỒN PHÁT SINH SỰ CỐ (Sẽ có 1 trong các nguồn liên kết bên dưới)
    rule_id BIGINT REFERENCES threshold_rules(id) ON DELETE SET NULL, -- Bị vi phạm bởi Luật SNMP Threshold (NULL nếu từ Syslog hoặc Ping Down)
    syslog_rule_id BIGINT REFERENCES syslog_rules(id) ON DELETE SET NULL, -- Bị vi phạm bởi Luật Syslog (NULL nếu từ SNMP)
    syslog_id BIGINT REFERENCES syslogs(id) ON DELETE SET NULL, -- Câu Syslog nguyên bản trực tiếp tạo ra sự cố này (Dùng tra cứu nhanh)
    
    severity VARCHAR(20) NOT NULL, -- Mức độ sự cố (WARNING, CRITICAL, DOWN)
    title VARCHAR(200) NOT NULL, -- Tiêu đề tóm tắt sự cố (VD: 'BGP Neighbor Down', 'CPU Usage Critical')
    message TEXT, -- Nội dung mô tả chi tiết sự cố
    camunda_process_id VARCHAR(64), -- ID quy trình xử lý gửi thông báo sự cố trên Camunda Workflow Engine
    status VARCHAR(30) DEFAULT 'OPEN', -- Trạng thái sự cố (OPEN: Mới phát hiện, ACKNOWLEDGED: Đã tiếp nhận, RESOLVED: Đã xử lý)
    acknowledged_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL, -- Người vận hành tiếp nhận xem sự cố
    acknowledged_at TIMESTAMP WITH TIME ZONE, -- Thời điểm tiếp nhận sự cố
    resolved_at TIMESTAMP WITH TIME ZONE, -- Thời điểm sự cố khôi phục về trạng thái bình thường
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm phát hiện sự cố
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật trạng thái sự cố
);

-- 5.2. Cấu hình các kênh gửi thông báo cảnh báo (Stalwart Email Server & Telegram Bot)
CREATE TABLE notification_configs (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính cấu hình kênh thông báo
    channel_name VARCHAR(50) NOT NULL, -- Tên đại diện kênh (VD: 'Stalwart Mail Server', 'Telegram SOC Bot')
    channel_type VARCHAR(20) NOT NULL, -- Loại kênh thông báo ('EMAIL', 'TELEGRAM')
    
    -- Cấu hình cho Telegram Bot
    bot_token VARCHAR(255), -- Token Bot Telegram
    chat_id VARCHAR(100),   -- ID phòng chat / User ID Telegram
    
    -- Cấu hình cho Stalwart Mail Server (SMTP)
    smtp_host VARCHAR(100),     -- Địa chỉ Stalwart Mail Server
    smtp_port INT DEFAULT 587,  -- Cổng SMTP (587 STARTTLS, 465 SSL/TLS, 25 Plain)
    smtp_username VARCHAR(100), -- Tài khoản gửi mail trên Stalwart (VD: alerts@yourdomain.com)
    smtp_password VARCHAR(100), -- Mật khẩu tài khoản mail
    
    is_active BOOLEAN DEFAULT TRUE, -- Trạng thái bật/tắt gửi thông báo qua kênh này
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo cấu hình
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật cấu hình
);


-- ------------------------------------------------------------
-- NHÓM 6: QUẢN LÝ TOPOLOGY & TRA CỨU IPAM (ARP/MAC)
-- ------------------------------------------------------------

-- 6.1. Lưu trữ sơ đồ liên kết không gian topology mạng (Nodes & Edges dạng đồ thị)
CREATE TABLE topology_maps (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính sơ đồ topology
    map_name VARCHAR(100) NOT NULL, -- Tên bản đồ topology (VD: Core Network Map, Campus A Topology...)
    nodes_data JSONB NOT NULL, -- Tọa độ, hình ảnh, thông tin danh sách nút (Devices) dạng JSON
    edges_data JSONB NOT NULL, -- Thông tin đường liên kết kết nối giữa các nút (Links/Connections) dạng JSON
    updated_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL, -- Người cập nhật sơ đồ gần nhất
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo sơ đồ
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm chỉnh sửa sơ đồ
);

-- 6.2. Bảng tra cứu định danh IP - MAC - Cổng (IPAM/Snooping hỗ trợ truy vết thiết bị cuối)
CREATE TABLE arp_mac_tables (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính bản ghi tra cứu
    ip_address VARCHAR(45) NOT NULL, -- Địa chỉ IP học được từ bảng ARP
    mac_address VARCHAR(17) NOT NULL, -- Địa chỉ MAC tương ứng học được
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, -- Thiết bị switch/router ghi nhận bảng ARP/MAC này
    interface_name VARCHAR(50) NOT NULL, -- Cổng kết nối trực tiếp thiết bị cuối đó
    vlan_id INT, -- ID Mạng ảo (VLAN ID) nếu có
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời gian gần nhất nhìn thấy MAC/IP này hoạt động
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Thời điểm cập nhật bản ghi
);


-- ------------------------------------------------------------
-- NHÓM 7: NHẬT KÝ THAO TÁC HỆ THỐNG (AUDIT LOGS)
-- ------------------------------------------------------------

-- 7.1. Nhật ký ghi lại mọi thao tác tác động hệ thống của người dùng trên Web UI (Phục vụ truy vết an ninh)
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY, -- Khóa chính bản ghi audit
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL, -- Tải khoản thực hiện thao tác
    action VARCHAR(100) NOT NULL, -- Thao tác thực hiện (VD: CREATE_DEVICE, DELETE_RULE, ACK_INCIDENT...)
    module VARCHAR(50) NOT NULL, -- Phân hệ bị tác động (VD: DEVICE_MGMT, THRESHOLD, INCIDENT...)
    ip_address VARCHAR(45), -- Địa chỉ IP của người dùng khi truy cập hệ thống
    details TEXT, -- Dữ liệu chi tiết trước/sau khi thay đổi (JSON string hoặc text description)
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL -- Mốc thời gian thực hiện thao tác
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

CREATE TRIGGER update_syslog_rules_updated_at
    BEFORE UPDATE ON syslog_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_oid_configs_updated_at
    BEFORE UPDATE ON oid_configs
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

ALTER TABLE devices ADD CONSTRAINT chk_device_type CHECK (device_type IN ('CORE_SWITCH', 'DISTRIBUTION_SWITCH', 'FIREWALL', 'ROUTER'));
ALTER TABLE devices ADD CONSTRAINT chk_device_status CHECK (status IN ('UP', 'DOWN', 'WARNING'));

ALTER TABLE device_credentials ADD CONSTRAINT chk_protocol_type CHECK (protocol_type IN ('SNMP_V1', 'SNMP_V2C', 'SYSLOG'));
ALTER TABLE device_credentials ADD CONSTRAINT chk_credential_port CHECK (port > 0 AND port <= 65535);

ALTER TABLE device_interfaces ADD CONSTRAINT chk_interface_speed CHECK (speed_bps >= 0);
ALTER TABLE device_interfaces ADD CONSTRAINT chk_interface_admin_status CHECK (admin_status IN ('UP', 'DOWN'));
ALTER TABLE device_interfaces ADD CONSTRAINT chk_interface_oper_status CHECK (oper_status IN ('UP', 'DOWN'));

ALTER TABLE oid_configs ADD CONSTRAINT chk_oid_metric_scope CHECK (metric_scope IN ('DEVICE', 'INTERFACE'));
ALTER TABLE oid_configs ADD CONSTRAINT chk_oid_data_type CHECK (data_type IN ('INTEGER', 'GAUGE32', 'COUNTER32', 'COUNTER64', 'STRING', 'TIMETICKS', 'FLOAT'));

ALTER TABLE threshold_rules ADD CONSTRAINT chk_warning_limit CHECK (warning_limit >= 0);
ALTER TABLE threshold_rules ADD CONSTRAINT chk_critical_limit CHECK (critical_limit >= 0);
ALTER TABLE threshold_rules ADD CONSTRAINT chk_threshold_limits CHECK (critical_limit >= warning_limit);
ALTER TABLE threshold_rules ADD CONSTRAINT chk_consecutive_occurrences CHECK (consecutive_occurrences > 0);
ALTER TABLE threshold_rules ADD CONSTRAINT chk_duration_seconds CHECK (duration_seconds >= 0);

ALTER TABLE syslog_rules ADD CONSTRAINT chk_syslog_rule_assign_severity CHECK (assign_severity IN ('WARNING', 'CRITICAL', 'DOWN'));

ALTER TABLE syslogs ADD CONSTRAINT chk_syslog_severity CHECK (severity IN ('EMERGENCY', 'ALERT', 'CRITICAL', 'ERROR', 'WARNING', 'NOTICE', 'INFORMATIONAL', 'DEBUG'));

ALTER TABLE incidents ADD CONSTRAINT chk_incident_severity CHECK (severity IN ('WARNING', 'CRITICAL', 'DOWN'));
ALTER TABLE incidents ADD CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'));

-- Ràng buộc kênh thông báo chỉ dùng EMAIL (Stalwart) và TELEGRAM
ALTER TABLE notification_configs ADD CONSTRAINT chk_channel_type CHECK (channel_type IN ('TELEGRAM', 'EMAIL'));
ALTER TABLE notification_configs ADD CONSTRAINT chk_smtp_port CHECK (smtp_port IS NULL OR (smtp_port > 0 AND smtp_port <= 65535));

ALTER TABLE arp_mac_tables ADD CONSTRAINT chk_vlan_id CHECK (vlan_id IS NULL OR (vlan_id >= 1 AND vlan_id <= 4094));

-- 4.2. KHU VỰC TẠO INDEXES TỐI ƯU HIỆU NĂNG TRUY VẤN

CREATE INDEX idx_devices_ip ON devices(ip_address);
CREATE INDEX idx_device_credentials_device_id ON device_credentials(device_id);
CREATE INDEX idx_device_interfaces_device_id ON device_interfaces(device_id);

CREATE INDEX idx_oid_configs_lookup ON oid_configs(metric_scope, device_id, device_type) WHERE is_active = TRUE;
CREATE INDEX idx_oid_configs_interface ON oid_configs(interface_id) WHERE interface_id IS NOT NULL AND is_active = TRUE;

-- INDEX GIN cho cột JSONB metrics để truy vấn cực nhanh theo key bất kỳ
CREATE INDEX idx_device_metrics_jsonb ON device_metrics USING GIN (metrics);
CREATE INDEX idx_device_metrics_device_time ON device_metrics(device_id, timestamp DESC);

CREATE INDEX idx_interface_metrics_jsonb ON interface_metrics USING GIN (metrics);
CREATE INDEX idx_interface_metrics_iface_time ON interface_metrics(interface_id, timestamp DESC);

CREATE INDEX idx_syslogs_device_time ON syslogs(device_id, timestamp DESC);
CREATE INDEX idx_syslogs_ip_time ON syslogs(ip_address, timestamp DESC);
CREATE INDEX idx_syslogs_severity ON syslogs(severity);

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_device_id ON incidents(device_id);
CREATE INDEX idx_incidents_syslog_id ON incidents(syslog_id) WHERE syslog_id IS NOT NULL;

CREATE INDEX idx_topology_nodes_jsonb ON topology_maps USING GIN (nodes_data);
CREATE INDEX idx_topology_edges_jsonb ON topology_maps USING GIN (edges_data);

CREATE INDEX idx_arp_mac_ip ON arp_mac_tables(ip_address);
CREATE INDEX idx_arp_mac_mac ON arp_mac_tables(mac_address);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);


