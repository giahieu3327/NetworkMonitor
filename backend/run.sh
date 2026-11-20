#!/bin/bash

ACTION=$1
SERVICE_NAME=$2
MAX_LOG_LINES=1000

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE_DIR="$BASE_DIR/$SERVICE_NAME"
LOG_DIR="$BASE_DIR/logs"
LOG_FILE="$LOG_DIR/$SERVICE_NAME.log"
JDK_DIR="$BASE_DIR/tools/jdk-21"

show_usage() {
    echo ""
    echo "Cú pháp: $0 <start|stop|restart|status|logs> <portal_service|monitor_service>"
    echo ""
    echo "Ví dụ:"
    echo "  $0 start portal_service"
    echo "  $0 stop portal_service"
    echo "  $0 restart portal_service"
    echo "  $0 status portal_service"
    echo "  $0 logs portal_service"
    echo ""
    echo "  $0 start monitor_service"
    echo "  $0 stop monitor_service"
    echo "  $0 restart monitor_service"
    echo "  $0 status monitor_service"
    echo "  $0 logs monitor_service"
    echo ""
    exit 1
}

if [ -z "$ACTION" ] || [ -z "$SERVICE_NAME" ]; then
    show_usage
fi

case "$SERVICE_NAME" in
    portal_service|monitor_service)
        ;;
    *)
        echo "Service không hợp lệ: $SERVICE_NAME"
        show_usage
        ;;
esac

mkdir -p "$LOG_DIR"

check_service_directory() {
    if [ ! -d "$SERVICE_DIR" ]; then
        echo "Không tìm thấy thư mục: $SERVICE_DIR"
        exit 1
    fi

    if [ ! -f "$SERVICE_DIR/mvnw" ]; then
        echo "Không tìm thấy: $SERVICE_DIR/mvnw"
        exit 1
    fi

    if [ ! -x "$SERVICE_DIR/mvnw" ]; then
        chmod +x "$SERVICE_DIR/mvnw" || exit 1
    fi
}

check_jdk() {
    if [ -x "$JDK_DIR/bin/java" ]; then
        export JAVA_HOME="$JDK_DIR"
        export PATH="$JAVA_HOME/bin:$PATH"

        echo "Java: $(java -version 2>&1 | head -n 1)"
        return
    fi

    if command -v java >/dev/null 2>&1; then
        export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
        export PATH="$JAVA_HOME/bin:$PATH"

        echo "Java: $(java -version 2>&1 | head -n 1)"
        return
    fi

    echo ""
    echo "Chưa có Java. Đang tải JDK 21..."
    echo ""

    mkdir -p "$BASE_DIR/tools"

    curl -L \
        -o "$BASE_DIR/tools/jdk21.tar.gz" \
        "https://download.oracle.com/java/21/archive/jdk-21.0.12_linux-x64_bin.tar.gz" || {
        echo "Không thể tải JDK 21."
        exit 1
    }

    rm -rf "$JDK_DIR"

    tar -xzf "$BASE_DIR/tools/jdk21.tar.gz" \
        -C "$BASE_DIR/tools" || {
        echo "Không thể giải nén JDK 21."
        rm -f "$BASE_DIR/tools/jdk21.tar.gz"
        exit 1
    }

    EXTRACTED_JDK=$(find "$BASE_DIR/tools" \
        -maxdepth 1 \
        -type d \
        -name "jdk-21*" |
        head -n 1)

    if [ -z "$EXTRACTED_JDK" ]; then
        echo "Không tìm thấy JDK sau khi giải nén."
        rm -f "$BASE_DIR/tools/jdk21.tar.gz"
        exit 1
    fi

    mv "$EXTRACTED_JDK" "$JDK_DIR"
    rm -f "$BASE_DIR/tools/jdk21.tar.gz"

    export JAVA_HOME="$JDK_DIR"
    export PATH="$JAVA_HOME/bin:$PATH"

    echo ""
    echo "JDK 21 cài đặt thành công."
    echo "Java: $(java -version 2>&1 | head -n 1)"
}

get_java_pid() {
    for PROC in /proc/[0-9]*; do
        PID="${PROC##*/}"

        [ "$PID" = "$$" ] && continue
        [ -r "$PROC/cmdline" ] || continue

        CWD=$(readlink -f "$PROC/cwd" 2>/dev/null) || continue
        [ "$CWD" = "$SERVICE_DIR" ] || continue

        CMD=$(tr '\0' ' ' < "$PROC/cmdline" 2>/dev/null)

        if echo "$CMD" | grep -Eq \
            "java .*com\.network_monitor\.${SERVICE_NAME}\."; then
            echo "$PID"
            return
        fi
    done
}

is_running() {
    [ -n "$(get_java_pid)" ]
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

    if is_running; then
        echo "Service $SERVICE_NAME đang chạy."
        echo ""
        echo "Java PID: $(get_java_pid)"
        echo ""
        return 0
    fi

    check_jdk
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

    # Ghi log liên tục và dùng cat để ghi đè giữ nguyên i-node không bị mất liên kết stream
    nohup bash -c "./mvnw spring-boot:run 2>&1 | while IFS= read -r line; do
        echo \"\$line\" >> \"$LOG_FILE\"
        if [ \$(wc -l < \"$LOG_FILE\") -gt $MAX_LOG_LINES ]; then
            tail -n $MAX_LOG_LINES \"$LOG_FILE\" > \"$LOG_FILE.tmp\"
            cat \"$LOG_FILE.tmp\" > \"$LOG_FILE\"
            rm -f \"$LOG_FILE.tmp\"
        fi
    done" >/dev/null 2>&1 &

    MAVEN_PID=$!

    echo "Đang chờ Spring Boot khởi động..."

    for i in {1..30}; do
        sleep 1

        JAVA_PID=$(get_java_pid)

        if [ -n "$JAVA_PID" ]; then
            echo ""
            echo ""
            echo "========================================================="
            echo " START THÀNH CÔNG"
            echo "========================================================="
            echo "Service : $SERVICE_NAME"
            echo "Java PID: $JAVA_PID"
            echo "Log     : $LOG_FILE (duy trì tối đa $MAX_LOG_LINES dòng mới nhất)"
            echo "========================================================="
            echo ""
            return 0
        fi

        if ! kill -0 "$MAVEN_PID" 2>/dev/null; then
            break
        fi

        printf "."
    done

    echo ""
    echo ""
    echo "START thất bại."
    echo ""
    tail -n 50 "$LOG_FILE"

    if kill -0 "$MAVEN_PID" 2>/dev/null; then
        kill_tree "$MAVEN_PID"
    fi

    exit 1
}

stop_service() {
    echo ""
    echo "========================================================="
    echo " STOP $SERVICE_NAME"
    echo "========================================================="
    echo ""

    JAVA_PID=$(get_java_pid)

    if [ -z "$JAVA_PID" ]; then
        echo "Service $SERVICE_NAME không chạy."
        echo ""
        return 0
    fi

    echo "Java PID: $JAVA_PID"
    echo ""

    kill_tree "$JAVA_PID"

    sleep 2

    JAVA_PID=$(get_java_pid)

    if [ -n "$JAVA_PID" ]; then
        echo ""
        echo "STOP chưa hoàn tất, đang force kill..."

        kill -9 "$JAVA_PID" 2>/dev/null

        sleep 1
    fi

    if [ -z "$(get_java_pid)" ]; then
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

    JAVA_PID=$(get_java_pid)

    echo "Service: $SERVICE_NAME"
    echo ""

    if [ -n "$JAVA_PID" ]; then
        echo "Spring Boot: RUNNING"
        echo "Java PID   : $JAVA_PID"
    else
        echo "Spring Boot: STOPPED"
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