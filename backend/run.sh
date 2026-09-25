#!/bin/bash

# Cú pháp: ./run.sh <start|stop|restart|status|logs> <tên_service>

ACTION=$1
SERVICE_NAME=$2
MAX_LOG_LINES=1000

show_usage() {
    echo "=== Cú pháp không hợp lệ ==="
    echo "Sử dụng: ./run.sh <start|stop|restart|status|logs> <tên_service>"
    exit 1
}

if [ -z "$ACTION" ] || [ -z "$SERVICE_NAME" ]; then
    show_usage
fi

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$BASE_DIR/logs"
PID_DIR="$BASE_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

LOG_FILE="$LOG_DIR/${SERVICE_NAME}.log"
PID_FILE="$PID_DIR/${SERVICE_NAME}.pid"
ROTATOR_PID_FILE="$PID_DIR/${SERVICE_NAME}_rotator.pid"

check_jdk() {
    if [ ! -d "$BASE_DIR/tools/jdk-21" ]; then
        echo "=== Chưa thấy JDK 21 trong backend. Đang tự động tải Oracle JDK 21... ==="
        mkdir -p "$BASE_DIR/tools"
        curl -L -o "$BASE_DIR/tools/jdk21.tar.gz" https://download.oracle.com/java/21/archive/jdk-21.0.12_linux-x64_bin.tar.gz
        
        echo "=== Đang giải nén JDK 21... ==="
        tar -xzf "$BASE_DIR/tools/jdk21.tar.gz" -C "$BASE_DIR/tools/"
        mv "$BASE_DIR/tools/jdk-21.0.12" "$BASE_DIR/tools/jdk-21"
        rm "$BASE_DIR/tools/jdk21.tar.gz"
        echo "=== Cài đặt JDK 21 nội bộ thành công! ==="
    fi

    export JAVA_HOME="$BASE_DIR/tools/jdk-21"
    export PATH="$JAVA_HOME/bin:$PATH"
}

is_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
        fi
    fi
    return 1
}

# Tiến trình chạy ngầm cắt log liên tục để duy trì tối đa 1000 dòng
start_log_rotator() {
    (
        while true; do
            sleep 10
            if [ -f "$LOG_FILE" ]; then
                LINE_COUNT=$(wc -l < "$LOG_FILE")
                if [ "$LINE_COUNT" -gt "$MAX_LOG_LINES" ]; then
                    TMP_FILE="${LOG_FILE}.tmp"
                    tail -n "$MAX_LOG_LINES" "$LOG_FILE" > "$TMP_FILE"
                    mv "$TMP_FILE" "$LOG_FILE"
                fi
            fi
        done
    ) &
    echo $! > "$ROTATOR_PID_FILE"
}

stop_log_rotator() {
    if [ -f "$ROTATOR_PID_FILE" ]; then
        R_PID=$(cat "$ROTATOR_PID_FILE")
        if ps -p "$R_PID" > /dev/null 2>&1; then
            kill "$R_PID" > /dev/null 2>&1
        fi
        rm -f "$ROTATOR_PID_FILE"
    fi
}

start_service() {
    if is_running; then
        echo "=== Service '$SERVICE_NAME' đang chạy với PID: $(cat "$PID_FILE") ==="
        exit 0
    fi

    if [ ! -d "$BASE_DIR/$SERVICE_NAME" ]; then
        echo "=== LỖI: Thư mục '$SERVICE_NAME' không tồn tại! ==="
        exit 1
    fi

    check_jdk

    echo "=== Đang khởi chạy ngầm '$SERVICE_NAME'... ==="
    cd "$BASE_DIR/$SERVICE_NAME" || exit 1

    nohup ./mvnw spring-boot:run > "$LOG_FILE" 2>&1 &
    
    NEW_PID=$!
    echo $NEW_PID > "$PID_FILE"
    
    start_log_rotator

    echo "=== Khởi chạy thành công! PID: $NEW_PID ==="
    echo "=== Log được giới hạn tối đa $MAX_LOG_LINES dòng tại: $LOG_FILE ==="
}

stop_service() {
    stop_log_rotator

    if is_running; then
        PID=$(cat "$PID_FILE")
        echo "=== Đang dừng service '$SERVICE_NAME' (PID: $PID)... ==="
        kill "$PID"
        
        for i in {1..10}; do
            if ps -p "$PID" > /dev/null 2>&1; then
                sleep 1
            else
                break
            fi
        done

        if ps -p "$PID" > /dev/null 2>&1; then
            echo "=== Service chưa dừng, đang thực hiện kill -9... ==="
            kill -9 "$PID"
        fi

        rm -f "$PID_FILE"
        echo "=== Đã dừng '$SERVICE_NAME' thành công! ==="
    else
        echo "=== Service '$SERVICE_NAME' hiện không hoạt động. ==="
    fi
}

status_service() {
    if is_running; then
        echo "=== Service '$SERVICE_NAME' ĐANG CHẠY (PID: $(cat "$PID_FILE")) ==="
    else
        echo "=== Service '$SERVICE_NAME' ĐÃ DỪNG ==="
    fi
}

view_logs() {
    if [ -f "$LOG_FILE" ]; then
        echo "=== Xem log của '$SERVICE_NAME' (Nhấn Ctrl+C để thoát) ==="
        tail -f -n 100 "$LOG_FILE"
    else
        echo "=== LỖI: Chưa có file log cho '$SERVICE_NAME' tại $LOG_FILE ==="
    fi
}

case "$ACTION" in
    start)
        start_service
        ;;
    stop)
        stop_service
        ;;
    restart)
        stop_service
        sleep 2
        start_service
        ;;
    status)
        status_service
        ;;
    logs)
        view_logs
        ;;
    *)
        show_usage
        ;;
esac