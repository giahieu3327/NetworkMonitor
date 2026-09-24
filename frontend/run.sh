#!/bin/bash

# Kiểm tra tham số đầu vào
SERVICE_NAME=$1
if [ -z "$SERVICE_NAME" ]; then
    echo "=== LỖI: Vui lòng nhập tên frontend service cần chạy! ==="
    echo "Cú pháp: ./run.sh <tên_service>"
    exit 1
fi

# Kiểm tra thư mục service có tồn tại không
if [ ! -d "$SERVICE_NAME" ]; then
    echo "=== LỖI: Thư mục '$SERVICE_NAME' không tồn tại! ==="
    exit 1
fi

# 1. Tự động tải Node.js 22 LTS nếu chưa có ở gốc frontend
if [ ! -d "tools/node" ]; then
    echo "=== Chưa thấy Node.js trong project. Đang tự động tải Node.js 22 LTS Portable... ==="
    mkdir -p tools
    curl -L -o tools/node.tar.xz https://nodejs.org/dist/v22.11.0/node-v22.11.0-linux-x64.tar.xz
    
    echo "=== Đang giải nén Node.js... ==="
    tar -xJf tools/node.tar.xz -C tools/
    mv tools/node-v22.11.0-linux-x64 tools/node
    rm tools/node.tar.xz
    echo "=== Cài đặt Node.js nội bộ thành công! ==="
fi

# 2. Thiết lập biến PATH ưu tiên dùng Node.js & npm tích hợp ở gốc frontend
export PATH="$(pwd)/tools/node/bin:$PATH"

echo "=== Đang sử dụng Node.js & npm tích hợp ==="
node -v
npm -v

# 3. Bỏ tham số tên service để truyền các tham số khác vào npm start nếu cần
shift

# 4. Truy cập vào thư mục frontend service tương ứng
cd "$SERVICE_NAME" || exit

# 5. Tự động chạy npm install nếu thư mục node_modules chưa tồn tại
if [ ! -d "node_modules" ]; then
    echo "=== Chưa có node_modules trong $SERVICE_NAME. Đang chạy npm install... ==="
    npm install
fi

# 6. Khởi chạy ứng dụng ReactJS
echo "=== Đang khởi chạy ReactJS ($SERVICE_NAME)... ==="
npm start "$@"