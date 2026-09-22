#!/bin/bash

# Tự động tải JDK 21 nếu chưa có trong project
if [ ! -d "tools/jdk-21" ]; then
    echo "=== Chưa thấy JDK 21 trong project. Đang tự động tải Oracle JDK 21... ==="
    mkdir -p tools
    curl -L -o tools/jdk21.tar.gz https://download.oracle.com/java/21/archive/jdk-21.0.12_linux-x64_bin.tar.gz
    
    echo "=== Đang giải nén JDK 21... ==="
    tar -xzf tools/jdk21.tar.gz -C tools/
    mv tools/jdk-21.0.12 tools/jdk-21
    rm tools/jdk21.tar.gz
    echo "=== Cài đặt JDK 21 nội bộ thành công! ==="
fi

# Thiết lập biến môi trường trỏ vào JDK nhúng nội bộ
export JAVA_HOME="$(pwd)/tools/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "=== Đang sử dụng JDK tích hợp ==="
java -version

echo "=== Đang khởi chạy Spring Boot ==="
./mvnw spring-boot:run "$@"
