#!/bin/bash

# Kiểm tra tham số đầu vào
SERVICE_NAME=$1
if [ -z "$SERVICE_NAME" ]; then
    echo "=== LỖI: Vui lòng nhập tên service cần chạy! ==="
    echo "Cú pháp: ./run.sh <tên_service>"
    exit 1
fi

# Kiểm tra thư mục service có tồn tại không
if [ ! -d "$SERVICE_NAME" ]; then
    echo "=== LỖI: Thư mục '$SERVICE_NAME' không tồn tại! ==="
    exit 1
fi

# 1. Tự động tải JDK 21 nếu chưa có ở gốc backend
if [ ! -d "tools/jdk-21" ]; then
    echo "=== Chưa thấy JDK 21 trong backend. Đang tự động tải Oracle JDK 21... ==="
    mkdir -p tools
    curl -L -o tools/jdk21.tar.gz https://download.oracle.com/java/21/archive/jdk-21.0.12_linux-x64_bin.tar.gz
    
    echo "=== Đang giải nén JDK 21... ==="
    tar -xzf tools/jdk21.tar.gz -C tools/
    mv tools/jdk-21.0.12 tools/jdk-21
    rm tools/jdk21.tar.gz
    echo "=== Cài đặt JDK 21 nội bộ thành công! ==="
fi

# 2. Thiết lập biến môi trường trỏ vào JDK nhúng nội bộ
export JAVA_HOME="$(pwd)/tools/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "=== Đang sử dụng JDK tích hợp ==="
java -version

# 3. Chuyển vào thư mục service và chạy Spring Boot (để nhận đúng file .env nội bộ)
echo "=== Đang khởi chạy $SERVICE_NAME ==="
shift # Bỏ tham số đầu tiên (tên service)
cd "$SERVICE_NAME" || exit
./mvnw spring-boot:run "$@"