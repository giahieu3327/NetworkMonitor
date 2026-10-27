#!/bin/bash

ACTION=$1
SERVICE_NAME=$2
MAX_LOG_LINES=1000

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE_DIR="$BASE_DIR/$SERVICE_NAME"
LOG_DIR="$BASE_DIR/logs"
LOG_FILE="$LOG_DIR/$SERVICE_NAME.log"

NODE_DIR="$BASE_DIR/tools/node"
NODE_VERSION="22.20.0"
NODE_TAR="$BASE_DIR/tools/node.tar.xz"

show_usage() {
    echo ""
    echo "Cú pháp: $0 <start|stop|restart|status|logs> <frontend_service>"
    echo ""
    echo "Ví dụ:"
    echo "  $0 start frontend_service"
    echo "  $0 stop frontend_service"
    echo "  $0 restart frontend_service"
    echo "  $0 status frontend_service"
    echo "  $0 logs frontend_service"
    echo ""
    exit 1
}

if [ -z "$ACTION" ] || [ -z "$SERVICE_NAME" ]; then
    show_usage
fi

if [ "$SERVICE_NAME" != "frontend_service" ]; then
    echo "Service không hợp lệ: $SERVICE_NAME"
    show_usage
fi

mkdir -p "$LOG_DIR"

check_service_directory() {
    if [ ! -d "$SERVICE_DIR" ]; then
        echo "Không tìm thấy thư mục: $SERVICE_DIR"
        exit 1
    fi

    if [ ! -f "$SERVICE_DIR/package.json" ]; then
        echo "Không tìm thấy: $SERVICE_DIR/package.json"
        exit 1
    fi
}

check_node() {
    if [ -x "$NODE_DIR/bin/node" ] && [ -x "$NODE_DIR/bin/npm" ]; then
        export PATH="$NODE_DIR/bin:$PATH"

        echo "Node: $(node --version)"
        echo "npm : $(npm --version)"
        return
    fi

    if command -v node >/dev/null 2>&1 && \
       command -v npm >/dev/null 2>&1; then

        echo "Node: $(node --version)"
        echo "npm : $(npm --version)"
        return
    fi

    echo ""
    echo "Chưa có Node.js."
    echo "Đang tải Node.js $NODE_VERSION..."
    echo ""

    mkdir -p "$BASE_DIR/tools"

    curl -L \
        -o "$NODE_TAR" \
        "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" || {
        echo ""
        echo "Không thể tải Node.js."
        exit 1
    }

    rm -rf "$NODE_DIR"

    tar -xJf "$NODE_TAR" \
        -C "$BASE_DIR/tools" || {
        echo "Không thể giải nén Node.js."
        rm -f "$NODE_TAR"
        exit 1
    }

    EXTRACTED_NODE="$BASE_DIR/tools/node-v${NODE_VERSION}-linux-x64"

    if [ ! -d "$EXTRACTED_NODE" ]; then
        echo "Không tìm thấy Node.js sau khi giải nén."
        rm -f "$NODE_TAR"
        exit 1
    fi

    mv "$EXTRACTED_NODE" "$NODE_DIR"
    rm -f "$NODE_TAR"

    export PATH="$NODE_DIR/bin:$PATH"

    echo ""
    echo "Node.js cài đặt thành công."
    echo "Node: $(node --version)"
    echo "npm : $(npm --version)"
}

get_service_pid() {
    for PROC in /proc/[0-9]*; do
        PID="${PROC##*/}"

        [ "$PID" = "$$" ] && continue
        [ -r "$PROC/cmdline" ] || continue

        CWD=$(readlink -f "$PROC/cwd" 2>/dev/null) || continue
        [ "$CWD" = "$SERVICE_DIR" ] || continue

        CMD=$(tr '\0' ' ' < "$PROC/cmdline" 2>/dev/null)

        if echo "$CMD" | grep -Eq '(^|/)npm start($| )'; then
            echo "$PID"
            return
        fi
    done
}

kill_tree() {
    local PID=$1

    [ -z "$PID" ] && return
    kill -0 "$PID" 2>/dev/null || return

    CHILDREN=$(pgrep -P "$PID" 2>/dev/null)

    for CHILD in $CHILDREN; do
        kill_tree "$CHILD"
    done

    kill "$PID" 2>/dev/null
}

trim_log() {
    [ -f "$LOG_FILE" ] || touch "$LOG_FILE"

    LINE_COUNT=$(wc -l < "$LOG_FILE")

    if [ "$LINE_COUNT" -gt "$MAX_LOG_LINES" ]; then
        tail -n "$MAX_LOG_LINES" "$LOG_FILE" > "$LOG_FILE.tmp"
        cat "$LOG_FILE.tmp" > "$LOG_FILE"
        rm -f "$LOG_FILE.tmp"
    fi
}

start_service() {
    echo ""
    echo "========================================================="
    echo " START $SERVICE_NAME"
    echo "========================================================="
    echo ""

    check_service_directory
    check_node

    SERVICE_PID=$(get_service_pid)

    if [ -n "$SERVICE_PID" ]; then
        echo ""
        echo "Frontend $SERVICE_NAME đang chạy."
        echo ""
        echo "PID: $SERVICE_PID"
        echo ""
        return 0
    fi

    trim_log

    {
        echo ""
        echo "========================================================="
        echo " STARTING $SERVICE_NAME"
        echo " Time: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "========================================================="
    } >> "$LOG_FILE"

    cd "$SERVICE_DIR" || exit 1

    echo ""
    echo "Đang khởi chạy '$SERVICE_NAME'..."
    echo ""

    # Ghi log liên tục và dùng cat để ghi đè giữ nguyên i-node không bị mất liên kết
    nohup bash -c "npm start 2>&1 | while IFS= read -r line; do
        echo \"\$line\" >> \"$LOG_FILE\"
        if [ \$(wc -l < \"$LOG_FILE\") -gt $MAX_LOG_LINES ]; then
            tail -n $MAX_LOG_LINES \"$LOG_FILE\" > \"$LOG_FILE.tmp\"
            cat \"$LOG_FILE.tmp\" > \"$LOG_FILE\"
            rm -f \"$LOG_FILE.tmp\"
        fi
    done" >/dev/null 2>&1 &

    NPM_PID=$!

    echo "npm PID: $NPM_PID"
    echo "Đang chờ frontend khởi động..."

    for i in {1..30}; do
        sleep 1

        SERVICE_PID=$(get_service_pid)

        if [ -n "$SERVICE_PID" ]; then
            echo ""
            echo ""
            echo "========================================================="
            echo " START THÀNH CÔNG"
            echo "========================================================="
            echo "Service: $SERVICE_NAME"
            echo "PID    : $SERVICE_PID"
            echo "Log    : $LOG_FILE (duy trì tối đa $MAX_LOG_LINES dòng mới nhất)"
            echo "========================================================="
            echo ""
            return 0
        fi

        if ! kill -0 "$NPM_PID" 2>/dev/null; then
            break
        fi

        printf "."
    done

    echo ""
    echo ""
    echo "START thất bại."
    echo ""
    tail -n 50 "$LOG_FILE"

    if kill -0 "$NPM_PID" 2>/dev/null; then
        kill_tree "$NPM_PID"
    fi

    exit 1
}

stop_service() {
    echo ""
    echo "========================================================="
    echo " STOP $SERVICE_NAME"
    echo "========================================================="
    echo ""

    SERVICE_PID=$(get_service_pid)

    if [ -z "$SERVICE_PID" ]; then
        echo "Frontend $SERVICE_NAME không chạy."
        echo ""
        return 0
    fi

    echo "PID: $SERVICE_PID"
    echo ""

    kill_tree "$SERVICE_PID"

    sleep 2

    SERVICE_PID=$(get_service_pid)

    if [ -n "$SERVICE_PID" ]; then
        echo ""
        echo "STOP chưa hoàn tất, đang force kill..."

        kill -9 "$SERVICE_PID" 2>/dev/null

        sleep 1
    fi

    if [ -z "$(get_service_pid)" ]; then
        echo ""
        echo "========================================================="
        echo " STOP THÀNH CÔNG"
        echo "========================================================="
        echo ""
    else
        echo ""
        echo "STOP thất bại."
        echo ""
        exit 1
    fi
}

restart_service() {
    stop_service
    sleep 2
    start_service
}

status_service() {
    echo ""
    echo "========================================================="
    echo " STATUS $SERVICE_NAME"
    echo "========================================================="
    echo ""

    SERVICE_PID=$(get_service_pid)

    echo "Service: $SERVICE_NAME"
    echo ""

    if [ -n "$SERVICE_PID" ]; then
        echo "Frontend: RUNNING"
        echo "PID     : $SERVICE_PID"
    else
        echo "Frontend: STOPPED"
    fi

    echo ""

    if [ -f "$LOG_FILE" ]; then
        echo "Log  : $LOG_FILE"
        echo "Lines: $(wc -l < "$LOG_FILE") / $MAX_LOG_LINES dòng mới nhất"
    else
        echo "Log  : Chưa có"
    fi

    echo ""
}

view_logs() {
    if [ ! -f "$LOG_FILE" ]; then
        echo "Chưa có log: $LOG_FILE"
        return 0
    fi

    echo ""
    echo "=== LOGS $SERVICE_NAME ($MAX_LOG_LINES DÒNG MỚI NHẤT) ==="
    echo "File: $LOG_FILE"
    echo "Nhấn Ctrl+C để thoát."
    echo ""

    tail -f -n "$MAX_LOG_LINES" "$LOG_FILE"
}

case "$ACTION" in
    start)
        start_service
        ;;
    stop)
        stop_service
        ;;
    restart)
        restart_service
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