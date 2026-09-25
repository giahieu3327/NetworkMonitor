# 🚀 Network Monitoring System (NMS)

Hệ thống quản lý và giám sát mạng doanh nghiệp tích hợp luồng phê duyệt (Camunda Workflow), xác thực tập trung (Keycloak OAuth2), báo cáo (JasperReports/Excel), caching (Redis) và xử lý sự kiện thời gian thực (Kafka).

---

## 🛠️ Yêu Cầu Tiền Đề (Prerequisites)

* **Docker** & **Docker Compose** *(Bắt buộc)*
* **Git** *(Tùy chọn — chỉ cần nếu dùng `git clone`, tải file ZIP từ GitHub thì không bắt buộc)*

---

## 🚀 Hướng Dẫn Vận Hành

### 1. Khởi Tạo Biến Môi Trường

Tạo file `.env` tại thư mục gốc của dự án (ngang hàng với `docker-compose.yml`):

```bash
cp .env.example .env
# Chỉnh sửa lại các thông số cấu hình, mật khẩu, cổng dịch vụ nếu cần

```

### 2. Khởi Chạy Các Dịch Vụ Hạ Tầng (Docker Container)

**Bước 2.1: Khởi chạy dàn Hạ tầng Core Services**

```bash
docker compose up -d

```

*(Bao gồm: PostgreSQL 16, Redis 7, Keycloak 26, Kafka 3.7 KRaft mode, Mailpit)*

**Bước 2.2: Khởi chạy dàn Công cụ Quản trị UI Dashboard (Tùy chọn)**

```bash
docker compose -f docker-compose.tools.yml up -d

```

*(Bao gồm: pgAdmin 4, Redis Insight, Kafka UI)*

---

### 3. Khởi Chạy Monitor Backend Service (`monitor-service`)

```bash
cd backend/monitor-service
./run.sh

```

---

### 4. Khởi Chạy Portal Backend Service (`portal_service`)

```bash
cd backend/portal_service
./run.sh

```

---

### 5. Khởi Chạy Frontend Application (`monitor-frontend`)

```bash
cd frontend/monitor-frontend
./run.sh

```

---

## 🌐 Danh Sách Cổng Dịch Vụ (Service Access Endpoints)

| Dịch Vụ / Thành Phần | URL Access | Mặc Định / Tài Khoản |
| --- | --- | --- |
| **Portal Service Backend** | `http://localhost:5000` | REST API / WebSocket |
| **Monitor Service Backend** | `http://localhost:5001` | REST API / Actuator Health |
| **Swagger UI (API Docs)** | `http://localhost:5000/swagger-ui.html` | Open API Specification |
| **React Frontend Web** | `http://localhost:3000` | Dashboard chính |
| **Keycloak IAM** | `http://localhost:8080` | Admin: `admin` / `Admin@123` |
| **Camunda Tasklist/Cockpit** | `http://localhost:5000/camunda` | Admin: `camunda-admin` / `Admin@123` |
| **Mailpit Web UI** | `http://localhost:8025` | Khai thác Mail Server ảo (SMTP: `1025`) |
| **pgAdmin 4 (Postgres UI)** | `http://localhost:8081` | Admin: `admin@monitor.com` / `Admin@123` |
| **Kafka UI Dashboard** | `http://localhost:8082` | Giám sát Kafka Cluster/Topics |
| **Redis Insight** | `http://localhost:5540` | Giám sát Redis Cache/Memory |