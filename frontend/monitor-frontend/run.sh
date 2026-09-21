#!/bin/bash

# 1. Tự động tải Node.js 22 LTS (Linux x64 Portable) nếu chưa có trong project
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

# 2. Thiết lập biến PATH ưu tiên dùng Node.js & npm trong project
export PATH="$(pwd)/tools/node/bin:$PATH"

echo "=== Đang sử dụng Node.js & npm tích hợp ==="
node -v
npm -v

# 3. Tự động chạy npm install nếu thư mục node_modules chưa tồn tại
if [ ! -d "node_modules" ]; then
    echo "=== Chưa có node_modules. Đang chạy npm install... ==="
    npm install
fi

# 4. Khởi chạy ứng dụng ReactJS
echo "=== Đang khởi chạy ReactJS... ==="
npm start "$@"
