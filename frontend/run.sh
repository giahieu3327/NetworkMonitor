#!/bin/bash

# =========================================================
# Frontend Service Runner
#
# Cú pháp:
#   ./run.sh <start|stop|restart|status|logs> <tên_service>
#
# Ví dụ:
#   ./run.sh start frontend_service
#   ./run.sh stop frontend_service
#   ./run.sh restart frontend_service
#   ./run.sh status frontend_service
#   ./run.sh logs frontend_service
# =========================================================

ACTION=$1
SERVICE_NAME=$2

MAX_LOG_LINES=1000
FRONTEND_PORT=3000

# =========================================================
# Kiểm tra cú pháp
# =========================================================

show_usage() {
    echo ""
    echo "=== Cú pháp không hợp lệ ==="
    echo ""
    echo "Sử dụng:"
    echo "  ./run.sh <start|stop|restart|status|logs> <tên_service>"
    echo ""
    echo "Ví dụ:"
    echo "  ./run.sh start frontend_service"
    echo "  ./run.sh stop frontend_service"
    echo "  ./run.sh restart frontend_service"
    echo "  ./run.sh status frontend_service"
    echo "  ./run.sh logs frontend_service"
    echo ""
    exit 1
}

if [ -z "$ACTION" ] || [ -z "$SERVICE_NAME" ]; then
    show_usage
fi

# =========================================================
# Thư mục
# =========================================================

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

LOG_DIR="$BASE_DIR/logs"
PID_DIR="$BASE_DIR/pids"

LOG_FILE="$LOG_DIR/${SERVICE_NAME}.log"
PID_FILE="$PID_DIR/${SERVICE_NAME}.pid"
ROTATOR_PID_FILE="$PID_DIR/${SERVICE_NAME}_rotator.pid"

mkdir -p "$LOG_DIR"
mkdir -p "$PID_DIR"

# =========================================================
# Kiểm tra Node.js
# =========================================================

check_node() {

    if [ ! -d "$BASE_DIR/tools/node" ]; then

        echo ""
        echo "=== Chưa thấy Node.js trong project. ==="
        echo "=== Đang tự động tải Node.js 22 LTS... ==="
        echo ""

        mkdir -p "$BASE_DIR/tools"

        curl -L \
            -o "$BASE_DIR/tools/node.tar.xz" \
            https://nodejs.org/dist/v22.11.0/node-v22.11.0-linux-x64.tar.xz

        if [ $? -ne 0 ]; then
            echo "=== LỖI: Không thể tải Node.js. ==="
            exit 1
        fi

        echo ""
        echo "=== Đang giải nén Node.js... ==="

        tar -xJf \
            "$BASE_DIR/tools/node.tar.xz" \
            -C "$BASE_DIR/tools/"

        if [ $? -ne 0 ]; then
            echo "=== LỖI: Không thể giải nén Node.js. ==="
            exit 1
        fi

        mv \
            "$BASE_DIR/tools/node-v22.11.0-linux-x64" \
            "$BASE_DIR/tools/node"

        rm -f "$BASE_DIR/tools/node.tar.xz"

        echo ""
        echo "=== Cài đặt Node.js nội bộ thành công! ==="
    fi

    export PATH="$BASE_DIR/tools/node/bin:$PATH"

    echo "=== Node.js: $(node --version) ==="
    echo "=== npm: $(npm --version) ==="
}

# =========================================================
# Kiểm tra PID service
# =========================================================

is_running() {

    if [ -f "$PID_FILE" ]; then

        PID=$(cat "$PID_FILE")

        if [ -n "$PID" ] && ps -p "$PID" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
        fi
    fi

    return 1
}

# =========================================================
# Lấy process đang chiếm port
# =========================================================

get_port_pids() {

    if command -v lsof > /dev/null 2>&1; then

        lsof -ti :"$FRONTEND_PORT" 2>/dev/null

    elif command -v fuser > /dev/null 2>&1; then

        fuser "$FRONTEND_PORT"/tcp 2>/dev/null

    else

        ss -ltnp 2>/dev/null \
            | grep ":$FRONTEND_PORT " \
            | grep -o 'pid=[0-9]*' \
            | cut -d= -f2
    fi
}

# =========================================================
# Kiểm tra port
# =========================================================

is_port_in_use() {

    PORT_PIDS=$(get_port_pids)

    if [ -n "$PORT_PIDS" ]; then
        return 0
    fi

    return 1
}

# =========================================================
# Hiển thị process đang chiếm port
# =========================================================

show_port_process() {

    echo ""
    echo "=== Process đang sử dụng port $FRONTEND_PORT ==="

    if command -v lsof > /dev/null 2>&1; then

        sudo lsof -i :"$FRONTEND_PORT"

    else

        ss -ltnp | grep ":$FRONTEND_PORT "
    fi

    echo ""
}

# =========================================================
# Kill process tree
# =========================================================

kill_process_tree() {

    TARGET_PID=$1
    SIGNAL=$2

    if [ -z "$TARGET_PID" ]; then
        return
    fi

    if ! ps -p "$TARGET_PID" > /dev/null 2>&1; then
        return
    fi

    # Lấy tất cả process con
    CHILDREN=$(pgrep -P "$TARGET_PID" 2>/dev/null)

    for CHILD_PID in $CHILDREN; do

        # Kill tiếp process con của process này
        kill_process_tree "$CHILD_PID" "$SIGNAL"

    done

    kill -"$SIGNAL" "$TARGET_PID" > /dev/null 2>&1
}

# =========================================================
# Log rotator
# =========================================================

start_log_rotator() {

    # Nếu rotator cũ còn chạy thì không tạo thêm
    if [ -f "$ROTATOR_PID_FILE" ]; then

        OLD_ROTATOR_PID=$(cat "$ROTATOR_PID_FILE")

        if [ -n "$OLD_ROTATOR_PID" ] && \
           ps -p "$OLD_ROTATOR_PID" > /dev/null 2>&1; then

            return
        fi

        rm -f "$ROTATOR_PID_FILE"
    fi

    (
        while true; do

            sleep 10

            if [ -f "$LOG_FILE" ]; then

                LINE_COUNT=$(wc -l < "$LOG_FILE")

                if [ "$LINE_COUNT" -gt "$MAX_LOG_LINES" ]; then

                    TMP_FILE="${LOG_FILE}.tmp"

                    tail -n "$MAX_LOG_LINES" \
                        "$LOG_FILE" > "$TMP_FILE"

                    mv "$TMP_FILE" "$LOG_FILE"
                fi
            fi

        done
    ) &

    ROTATOR_PID=$!

    echo "$ROTATOR_PID" > "$ROTATOR_PID_FILE"
}

# =========================================================
# Stop log rotator
# =========================================================

stop_log_rotator() {

    if [ -f "$ROTATOR_PID_FILE" ]; then

        R_PID=$(cat "$ROTATOR_PID_FILE")

        if [ -n "$R_PID" ] && \
           ps -p "$R_PID" > /dev/null 2>&1; then

            kill "$R_PID" > /dev/null 2>&1

            sleep 1

            if ps -p "$R_PID" > /dev/null 2>&1; then
                kill -9 "$R_PID" > /dev/null 2>&1
            fi
        fi

        rm -f "$ROTATOR_PID_FILE"
    fi
}

# =========================================================
# Dọn process React đang chiếm port
# =========================================================

cleanup_port() {

    PORT_PIDS=$(get_port_pids)

    if [ -z "$PORT_PIDS" ]; then
        return
    fi

    echo ""
    echo "=== Phát hiện process đang chiếm port $FRONTEND_PORT ==="

    for PORT_PID in $PORT_PIDS; do

        echo "=== PID: $PORT_PID ==="

        # Không kill chính shell/run.sh
        if [ "$PORT_PID" = "$$" ]; then
            continue
        fi

        kill_process_tree "$PORT_PID" "TERM"
    done

    echo "=== Đang chờ process dừng... ==="

    for i in {1..5}; do

        if ! is_port_in_use; then
            break
        fi

        sleep 1
    done

    # Nếu vẫn còn process -> kill -9
    PORT_PIDS=$(get_port_pids)

    if [ -n "$PORT_PIDS" ]; then

        echo "=== Process vẫn còn chạy. Đang kill -9... ==="

        for PORT_PID in $PORT_PIDS; do

            if [ "$PORT_PID" = "$$" ]; then
                continue
            fi

            kill_process_tree "$PORT_PID" "KILL"
        done
    fi

    sleep 1

    if is_port_in_use; then

        echo "=== LỖI: Không thể giải phóng port $FRONTEND_PORT ==="

        show_port_process

        return 1
    fi

    echo "=== Đã giải phóng port $FRONTEND_PORT ==="

    return 0
}

# =========================================================
# START SERVICE
# =========================================================

start_service() {

    echo ""
    echo "========================================================="
    echo "              START FRONTEND SERVICE"
    echo "========================================================="
    echo ""

    # -------------------------------------------------------
    # Kiểm tra service đã chạy theo PID
    # -------------------------------------------------------

    if is_running; then

        PID=$(cat "$PID_FILE")

        echo "=== Frontend Service '$SERVICE_NAME' đang chạy ==="
        echo "=== PID: $PID ==="
        echo "=== Port: $FRONTEND_PORT ==="
        echo ""

        return 0
    fi

    # -------------------------------------------------------
    # Kiểm tra thư mục frontend
    # -------------------------------------------------------

    if [ ! -d "$BASE_DIR/$SERVICE_NAME" ]; then

        echo "=== LỖI: Thư mục '$SERVICE_NAME' không tồn tại! ==="
        echo "=== Đường dẫn mong muốn: $BASE_DIR/$SERVICE_NAME ==="

        exit 1
    fi

    # -------------------------------------------------------
    # Node.js
    # -------------------------------------------------------

    check_node

    # -------------------------------------------------------
    # Kiểm tra port 3000
    # -------------------------------------------------------

    if is_port_in_use; then

        echo ""
        echo "=== LỖI: Port $FRONTEND_PORT đang được sử dụng! ==="

        show_port_process

        echo "=== Service chưa được khởi động. ==="
        echo "=== Hãy chạy: ./run.sh stop $SERVICE_NAME ==="
        echo ""

        exit 1
    fi

    # -------------------------------------------------------
    # Vào thư mục frontend
    # -------------------------------------------------------

    cd "$BASE_DIR/$SERVICE_NAME" || exit 1

    # -------------------------------------------------------
    # npm install
    # -------------------------------------------------------

    if [ ! -d "node_modules" ]; then

        echo ""
        echo "=== Chưa có node_modules trong $SERVICE_NAME ==="
        echo "=== Đang chạy npm install... ==="
        echo ""

        npm install

        if [ $? -ne 0 ]; then

            echo ""
            echo "=== LỖI: npm install thất bại! ==="

            exit 1
        fi
    fi

    # -------------------------------------------------------
    # Xóa PID cũ nếu có
    # -------------------------------------------------------

    rm -f "$PID_FILE"

    # -------------------------------------------------------
    # Start React
    # -------------------------------------------------------

    echo ""
    echo "=== Đang khởi chạy ngầm ReactJS... ==="
    echo "=== Service: $SERVICE_NAME ==="
    echo "=== Port: $FRONTEND_PORT ==="
    echo ""

    nohup npm start > "$LOG_FILE" 2>&1 &

    NEW_PID=$!

    echo "$NEW_PID" > "$PID_FILE"

    echo "=== npm PID: $NEW_PID ==="

    # -------------------------------------------------------
    # Start log rotator
    # -------------------------------------------------------

    start_log_rotator

    # -------------------------------------------------------
    # Chờ React khởi động
    # -------------------------------------------------------

    echo "=== Đang chờ ReactJS khởi động... ==="

    STARTED=false

    for i in {1..15}; do

        sleep 1

        if is_port_in_use; then
            STARTED=true
            break
        fi

        # npm chết trước khi React start
        if ! ps -p "$NEW_PID" > /dev/null 2>&1; then
            break
        fi
    done

    # -------------------------------------------------------
    # Kiểm tra kết quả
    # -------------------------------------------------------

    if [ "$STARTED" = true ]; then

        echo ""
        echo "========================================================="
        echo "        FRONTEND STARTED SUCCESSFULLY"
        echo "========================================================="
        echo "Service : $SERVICE_NAME"
        echo "PID     : $NEW_PID"
        echo "Port    : $FRONTEND_PORT"
        echo "Log     : $LOG_FILE"
        echo "========================================================="
        echo ""

    else

        echo ""
        echo "========================================================="
        echo "             FRONTEND START FAILED"
        echo "========================================================="

        echo ""
        echo "=== Kiểm tra log: ==="
        echo ""

        tail -n 30 "$LOG_FILE"

        echo ""
        echo "========================================================="

        rm -f "$PID_FILE"

        stop_log_rotator

        exit 1
    fi
}

# =========================================================
# STOP SERVICE
# =========================================================

stop_service() {

    echo ""
    echo "========================================================="
    echo "               STOP FRONTEND SERVICE"
    echo "========================================================="
    echo ""

    stop_log_rotator

    # -------------------------------------------------------
    # Stop bằng PID file
    # -------------------------------------------------------

    if [ -f "$PID_FILE" ]; then

        PID=$(cat "$PID_FILE")

        if [ -n "$PID" ] && \
           ps -p "$PID" > /dev/null 2>&1; then

            echo "=== Đang dừng '$SERVICE_NAME' ==="
            echo "=== PID: $PID ==="

            # Kill toàn bộ process tree bằng TERM
            kill_process_tree "$PID" "TERM"

            echo "=== Đang chờ process dừng... ==="

            for i in {1..10}; do

                if ! ps -p "$PID" > /dev/null 2>&1; then
                    break
                fi

                sleep 1
            done

            # Nếu npm vẫn còn -> KILL
            if ps -p "$PID" > /dev/null 2>&1; then

                echo "=== Service chưa dừng hẳn ==="
                echo "=== Đang thực hiện kill -9... ==="

                kill_process_tree "$PID" "KILL"

                sleep 1
            fi

        else

            echo "=== PID file tồn tại nhưng process không còn. ==="
        fi

        rm -f "$PID_FILE"

    else

        echo "=== Không tìm thấy PID file của '$SERVICE_NAME'. ==="
    fi

    # -------------------------------------------------------
    # QUAN TRỌNG:
    # Kiểm tra port 3000 vì React có thể vẫn còn process
    # -------------------------------------------------------

    if is_port_in_use; then

        echo ""
        echo "=== Phát hiện process React vẫn đang chiếm port $FRONTEND_PORT ==="

        cleanup_port

        if [ $? -ne 0 ]; then

            echo ""
            echo "=== LỖI: Không thể giải phóng port $FRONTEND_PORT ==="

            show_port_process

            exit 1
        fi
    fi

    # -------------------------------------------------------
    # Cleanup
    # -------------------------------------------------------

    rm -f "$PID_FILE"
    rm -f "$ROTATOR_PID_FILE"

    echo ""
    echo "========================================================="
    echo "        FRONTEND SERVICE STOPPED SUCCESSFULLY"
    echo "========================================================="
    echo "Service : $SERVICE_NAME"
    echo "Port    : $FRONTEND_PORT"
    echo "========================================================="
    echo ""
}

# =========================================================
# STATUS SERVICE
# =========================================================

status_service() {

    echo ""
    echo "========================================================="
    echo "              FRONTEND SERVICE STATUS"
    echo "========================================================="
    echo ""

    # -------------------------------------------------------
    # PID service
    # -------------------------------------------------------

    if is_running; then

        PID=$(cat "$PID_FILE")

        echo "Service : $SERVICE_NAME"
        echo "Status  : RUNNING"
        echo "PID     : $PID"

    else

        echo "Service : $SERVICE_NAME"
        echo "Status  : STOPPED"
    fi

    # -------------------------------------------------------
    # Port
    # -------------------------------------------------------

    echo ""

    if is_port_in_use; then

        PORT_PIDS=$(get_port_pids)

        echo "Port    : $FRONTEND_PORT"
        echo "Status  : IN USE"
        echo "PID     : $PORT_PIDS"

    else

        echo "Port    : $FRONTEND_PORT"
        echo "Status  : FREE"
    fi

    # -------------------------------------------------------
    # Log
    # -------------------------------------------------------

    echo ""
    echo "Log     : $LOG_FILE"

    if [ -f "$LOG_FILE" ]; then

        LINE_COUNT=$(wc -l < "$LOG_FILE")

        echo "Lines   : $LINE_COUNT / $MAX_LOG_LINES"

    else

        echo "Log     : Chưa tồn tại"
    fi

    echo ""
    echo "========================================================="
    echo ""
}

# =========================================================
# VIEW LOGS
# =========================================================

view_logs() {

    if [ -f "$LOG_FILE" ]; then

        echo ""
        echo "========================================================="
        echo "              FRONTEND SERVICE LOGS"
        echo "========================================================="
        echo "Service: $SERVICE_NAME"
        echo "File   : $LOG_FILE"
        echo ""
        echo "Nhấn Ctrl+C để thoát."
        echo "========================================================="
        echo ""

        tail -f -n 100 "$LOG_FILE"

    else

        echo ""
        echo "=== LỖI: Chưa có file log ==="
        echo "=== $LOG_FILE ==="
        echo ""
    fi
}

# =========================================================
# MAIN
# =========================================================

case "$ACTION" in

    start)
        start_service
        ;;

    stop)
        stop_service
        ;;

    restart)

        echo ""
        echo "=== Đang restart Frontend Service '$SERVICE_NAME'... ==="
        echo ""

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