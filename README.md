# Network Monitoring System (NMS)

Hệ thống quản lý và giám sát mạng doanh nghiệp tích hợp luồng phê duyệt (**Camunda Workflow**), xác thực tập trung (**Keycloak OAuth2**), báo cáo (**JasperReports/Excel**), caching (**Redis**) và hệ thống thông báo email tự lưu trữ (**Stalwart Mail Server & SnappyMail**).

---

## Yêu Cầu Tiền Đề (Prerequisites)

- **Docker** & **Docker Compose** *(Bắt buộc)*
- **Git** *(Tùy chọn — chỉ cần nếu dùng `git clone`, tải file ZIP từ GitHub thì không bắt buộc)*
- Các toolchain **JDK 21** và **Node.js 22** đã được đóng gói sẵn (portable) trong dự án, không cần cài đặt thủ công lên hệ điều hành.

---

## Hướng Dẫn Vận Hành

### 1. Khởi Tạo Biến Môi Trường (`.env`)

Dự án có tổng cộng **3 file `.env.example`** nằm tại 3 thư mục khác nhau. Trước khi khởi chạy, bạn cần sao chép từng file `.env.example` thành `.env` và **thay đổi toàn bộ các giá trị `yourpassword`** sang mật khẩu tự đặt (ví dụ: `Admin@123456` — lưu ý mật khẩu của PostgreSQL, Redis, Keycloak, Stalwart phải khớp nhau giữa thư mục `docker` và `portal_service`).

**1.1. Cấu hình `.env` cho Hạ tầng Docker:**
```bash
cd docker
cp .env.example .env
# Mở file .env và đổi các trường password (POSTGRES_PASSWORD, REDIS_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, PGADMIN_DEFAULT_PASSWORD...)
```

**1.2. Cấu hình `.env` cho Backend (`portal_service`):**
```bash
cd ../backend/portal_service
cp .env.example .env
# Mở file .env và đổi toàn bộ giá trị `yourpassword` khớp với cấu hình Docker (ví dụ: Admin@123456)
# Cập nhật thêm FRONTEND_BASE_URL nếu chạy qua IP LAN (ví dụ: [http://172.16.10.187:3000](http://172.16.10.187:3000))
```

**1.3. Cấu hình `.env` cho Frontend (`frontend_service`):**
```bash
cd ../../frontend/frontend_service
cp .env.example .env
# Kiểm tra và cập nhật URL trỏ về Backend / Keycloak nếu cần
```

---

### 2. Khởi Chạy Các Dịch Vụ Hạ Tầng (Docker Container)

Di chuyển vào thư mục `docker/` để khởi chạy các container hạ tầng:

```bash
cd ../../docker
```

**Bước 2.1: Khởi chạy dàn Hạ tầng Core Services**
```bash
docker compose up -d
```
*(Bao gồm: PostgreSQL 18.6, Redis 8.10, Keycloak 26.7, Stalwart Mail Server v0.16.24, SnappyMail v2.38.2)*

**Bước 2.2: Khởi chạy dàn Công cụ Quản trị UI Dashboard (Tùy chọn)**
```bash
docker compose -f docker-compose.tools.yml up -d
```
*(Bao gồm: pgAdmin 4, Redis Insight)*

---

### 3. Khởi Chạy Backend Service (`portal_service`)

Di chuyển vào thư mục `backend/` và dùng script quản lý tiến trình chạy nền:

```bash
cd ../backend
./run.sh start portal_service
```
*(Các lệnh quản lý khác: `./run.sh stop portal_service`, `./run.sh restart portal_service`, `./run.sh status portal_service`)*

---

### 4. Khởi Chạy Frontend Application (`frontend_service`)

Di chuyển vào thư mục `frontend/` và khởi chạy dịch vụ giao diện:

```bash
cd ../frontend
./run.sh start frontend_service
```
*(Các lệnh quản lý khác: `./run.sh stop frontend_service`, `./run.sh restart frontend_service`, `./run.sh status frontend_service`)*

---

## Danh Sách Cổng Dịch Vụ (Service Access Endpoints)

| Dịch Vụ / Thành Phần | URL Access | Mặc Định / Tài Khoản |
| --- | --- | --- |
| **React Frontend Web** | `http://localhost:3000` | Dashboard chính (`admin` / `<mật_khẩu_đã_đặt>`) |
| **Portal Service Backend** | `http://localhost:5000` | REST API / WebSocket |
| **Swagger UI (API Docs)** | `http://localhost:5000/swagger-ui.html` | OpenAPI Specification |
| **Camunda Tasklist / Cockpit** | `http://localhost:5000/camunda` | Admin: `camunda-admin` / `<mật_khẩu_đã_đặt>` |
| **Keycloak IAM** | `http://localhost:8080` | Admin: `admin` / `<mật_khẩu_đã_đặt>` |
| **Stalwart Mail Admin UI** | `http://localhost:8090` | Quản trị Mail Server (SMTP: `25/465/587`, IMAP: `143/993`) |
| **SnappyMail Webmail** | `http://localhost:8888` | Giao diện đọc/gửi email người dùng |
| **pgAdmin 4 (Postgres UI)** | `http://localhost:8081` | Admin: `admin@monitor.com` / `<mật_khẩu_đã_đặt>` |
| **Redis Insight** | `http://localhost:5540` | Giám sát Redis Cache / Memory |