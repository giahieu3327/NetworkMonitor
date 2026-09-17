# Monitor Project

## 📂 Cấu trúc
- backend/monitor-backend: Spring Boot backend
- frontend/monitor-frontend: React frontend
- docker/: cấu hình Postgres, Keycloak
- docker-compose.yml: Postgres, Redis, Keycloak
- docker-compose.tools.yml: pgAdmin, Redis Insight
- .env.example: mẫu biến môi trường

---

## 🚀 Cách chạy

### 1. Chuẩn bị biến môi trường
cp .env.example .env
# chỉnh lại mật khẩu/email nếu cần

### 2. Chạy Docker services
docker compose up -d
docker compose -f docker-compose.tools.yml up -d

### 3. Chạy Backend
cd backend/monitor-backend
mvn clean install -DskipTests
mvn spring-boot:run

### 4. Chạy Frontend
cd frontend/monitor-frontend
npm install
npm start

---

## 📊 Kết quả
- Backend: http://localhost:5000
- Frontend: http://localhost:3000
- Keycloak: http://localhost:8080
- pgAdmin: http://localhost:8081
- Redis Insight: http://localhost:5540
