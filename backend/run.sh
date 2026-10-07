#!/bin/bash

# =========================================================
# Backend Service Runner
#
# Có 2 microservice:
#
#   portal_service  -> port 5000
#   monitor_service -> port 5001
#
# Cú pháp:
#   ./run.sh <start|stop|restart|status|logs> <tên_service>
#
# Ví dụ:
#   ./run.sh start portal_service
#   ./run.sh stop portal_service
#   ./run.sh restart portal_service
#   ./run.sh status portal_service
#   ./run.sh logs portal_service
#
#   ./run.sh start monitor_service
#   ./run.sh stop monitor_service
#   ./run.sh restart monitor_service
#   ./run.sh status monitor_service
#   ./run.sh logs monitor_service
# =========================================================


# =========================================================
# INPUT
# =========================================================

ACTION=$1
SERVICE_NAME=$2

MAX_LOG_LINES=1000


# =========================================================
# SERVICE PORT
# =========================================================

case "$SERVICE_NAME" in

    portal_service)
        SERVICE_PORT=5000
        ;;

    monitor_service)
        SERVICE_PORT=5001
        ;;

    *)
        SERVICE_PORT=""
        ;;

esac


# =========================================================
# USAGE
# =========================================================

show_usage() {

    echo ""
    echo "=== Cú pháp không hợp lệ ==="
    echo ""
    echo "Sử dụng:"
    echo "  ./run.sh <start|stop|restart|status|logs> <tên_service>"
    echo ""
    echo "Service:"
    echo "  portal_service   -> port 5000"
    echo "  monitor_service  -> port 5001"
    echo ""
    echo "Ví dụ:"
    echo "  ./run.sh start portal_service"
    echo "  ./run.sh stop portal_service"
    echo "  ./run.sh restart portal_service"
    echo "  ./run.sh status portal_service"
    echo "  ./run.sh logs portal_service"
    echo ""
    echo "  ./run.sh start monitor_service"
    echo "  ./run.sh stop monitor_service"
    echo "  ./run.sh restart monitor_service"
    echo "  ./run.sh status monitor_service"
    echo "  ./run.sh logs monitor_service"
    echo ""

    exit 1
}


# =========================================================
# VALIDATE INPUT
# =========================================================

if [ -z "$ACTION" ] || [ -z "$SERVICE_NAME" ]; then
    show_usage
fi


if [ -z "$SERVICE_PORT" ]; then

    echo ""
    echo "=== LỖI: Service '$SERVICE_NAME' không hợp lệ! ==="
    echo ""

    echo "Service hợp lệ:"
    echo "  portal_service"
    echo "  monitor_service"
    echo ""

    exit 1
fi


# =========================================================
# BASE DIRECTORY
# =========================================================

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

LOG_DIR="$BASE_DIR/logs"
PID_DIR="$BASE_DIR/pids"


mkdir -p "$LOG_DIR"
mkdir -p "$PID_DIR"


# =========================================================
# FILES
# =========================================================

LOG_FILE="$LOG_DIR/${SERVICE_NAME}.log"

PID_FILE="$PID_DIR/${SERVICE_NAME}.pid"

ROTATOR_PID_FILE="$PID_DIR/${SERVICE_NAME}_rotator.pid"


# =========================================================
# CHECK JDK 21
# =========================================================

check_jdk() {

    if [ ! -d "$BASE_DIR/tools/jdk-21" ]; then

        echo ""
        echo "=== Chưa thấy JDK 21 trong backend. ==="
        echo "=== Đang tự động tải Oracle JDK 21... ==="
        echo ""

        mkdir -p "$BASE_DIR/tools"

        curl -L \
            -o "$BASE_DIR/tools/jdk21.tar.gz" \
            https://download.oracle.com/java/21/archive/jdk-21.0.12_linux-x64_bin.tar.gz


        if [ $? -ne 0 ]; then

            echo ""
            echo "=== LỖI: Không thể tải Oracle JDK 21! ==="
            echo ""

            exit 1
        fi


        echo ""
        echo "=== Đang giải nén JDK 21... ==="


        tar -xzf \
            "$BASE_DIR/tools/jdk21.tar.gz" \
            -C "$BASE_DIR/tools/"


        if [ $? -ne 0 ]; then

            echo ""
            echo "=== LỖI: Không thể giải nén JDK 21! ==="
            echo ""

            exit 1
        fi


        mv \
            "$BASE_DIR/tools/jdk-21.0.12" \
            "$BASE_DIR/tools/jdk-21"


        rm -f "$BASE_DIR/tools/jdk21.tar.gz"


        echo ""
        echo "=== Cài đặt JDK 21 nội bộ thành công! ==="
    fi


    export JAVA_HOME="$BASE_DIR/tools/jdk-21"

    export PATH="$JAVA_HOME/bin:$PATH"


    echo "=== Java: $(java -version 2>&1 | head -n 1) ==="
    echo "=== JAVA_HOME: $JAVA_HOME ==="
}


# =========================================================
# CHECK SERVICE PID
# =========================================================

is_running() {

    if [ -f "$PID_FILE" ]; then

        PID=$(cat "$PID_FILE")


        if [ -n "$PID" ] && \
           ps -p "$PID" > /dev/null 2>&1; then

            return 0

        else

            rm -f "$PID_FILE"

        fi

    fi


    return 1
}


# =========================================================
# GET PROCESS USING PORT
# =========================================================

get_port_pids() {

    if command -v lsof > /dev/null 2>&1; then

        lsof -ti :"$SERVICE_PORT" 2>/dev/null

    elif command -v fuser > /dev/null 2>&1; then

        fuser "$SERVICE_PORT"/tcp 2>/dev/null

    else

        ss -ltnp 2>/dev/null \
            | grep ":$SERVICE_PORT " \
            | grep -o 'pid=[0-9]*' \
            | cut -d= -f2

    fi
}


# =========================================================
# CHECK PORT
# =========================================================

is_port_in_use() {

    PORT_PIDS=$(get_port_pids)


    if [ -n "$PORT_PIDS" ]; then
        return 0
    fi


    return 1
}


# =========================================================
# SHOW PORT PROCESS
# =========================================================

show_port_process() {

    echo ""
    echo "=== Process đang sử dụng port $SERVICE_PORT ==="
    echo ""


    if command -v lsof > /dev/null 2>&1; then

        lsof -i :"$SERVICE_PORT"

    else

        ss -ltnp | grep ":$SERVICE_PORT "

    fi


    echo ""
}


# =========================================================
# KILL PROCESS TREE
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


    CHILDREN=$(pgrep -P "$TARGET_PID" 2>/dev/null)


    for CHILD_PID in $CHILDREN; do

        kill_process_tree "$CHILD_PID" "$SIGNAL"

    done


    kill -"$SIGNAL" "$TARGET_PID" > /dev/null 2>&1
}


# =========================================================
# LOG ROTATOR
# =========================================================

start_log_rotator() {

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
# STOP LOG ROTATOR
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
# CLEANUP PORT
# =========================================================

cleanup_port() {

    PORT_PIDS=$(get_port_pids)


    if [ -z "$PORT_PIDS" ]; then
        return 0
    fi


    echo ""
    echo "=== Phát hiện process đang chiếm port $SERVICE_PORT ==="


    for PORT_PID in $PORT_PIDS; do

        echo "=== PID: $PORT_PID ==="


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

        echo ""
        echo "=== LỖI: Không thể giải phóng port $SERVICE_PORT ==="

        show_port_process

        return 1

    fi


    echo "=== Đã giải phóng port $SERVICE_PORT ==="

    return 0
}


# =========================================================
# START SERVICE
# =========================================================

start_service() {

    echo ""
    echo "========================================================="
    echo "                 START BACKEND SERVICE"
    echo "========================================================="
    echo ""
    echo "Service : $SERVICE_NAME"
    echo "Port    : $SERVICE_PORT"
    echo ""


    # -------------------------------------------------------
    # CHECK PID
    # -------------------------------------------------------

    if is_running; then

        PID=$(cat "$PID_FILE")


        echo "=== Service '$SERVICE_NAME' đang chạy ==="
        echo "=== PID : $PID ==="
        echo "=== Port: $SERVICE_PORT ==="
        echo ""

        return 0
    fi


    # -------------------------------------------------------
    # CHECK DIRECTORY
    # -------------------------------------------------------

    if [ ! -d "$BASE_DIR/$SERVICE_NAME" ]; then

        echo "=== LỖI: Thư mục '$SERVICE_NAME' không tồn tại! ==="
        echo "=== Đường dẫn: $BASE_DIR/$SERVICE_NAME ==="

        exit 1
    fi


    # -------------------------------------------------------
    # CHECK JDK
    # -------------------------------------------------------

    check_jdk


    # -------------------------------------------------------
    # CHECK PORT
    # -------------------------------------------------------

    if is_port_in_use; then

        echo ""
        echo "=== LỖI: Port $SERVICE_PORT đang được sử dụng! ==="

        show_port_process

        echo "=== Service chưa được khởi động. ==="
        echo ""

        exit 1
    fi


    # -------------------------------------------------------
    # ENTER SERVICE DIRECTORY
    # -------------------------------------------------------

    cd "$BASE_DIR/$SERVICE_NAME" || exit 1


    # -------------------------------------------------------
    # CHECK MAVEN WRAPPER
    # -------------------------------------------------------

    if [ ! -f "./mvnw" ]; then

        echo ""
        echo "=== LỖI: Không tìm thấy mvnw trong '$SERVICE_NAME' ==="
        echo ""

        exit 1
    fi


    if [ ! -x "./mvnw" ]; then

        echo "=== mvnw chưa có quyền thực thi. Đang chmod +x... ==="

        chmod +x ./mvnw
    fi


    # -------------------------------------------------------
    # START SPRING BOOT
    # -------------------------------------------------------

    echo ""
    echo "=== Đang khởi chạy ngầm '$SERVICE_NAME'... ==="
    echo "=== Spring Boot port: $SERVICE_PORT ==="
    echo ""


    nohup ./mvnw spring-boot:run \
        > "$LOG_FILE" 2>&1 &


    NEW_PID=$!


    echo "$NEW_PID" > "$PID_FILE"


    # -------------------------------------------------------
    # START LOG ROTATOR
    # -------------------------------------------------------

    start_log_rotator


    echo "=== Maven PID: $NEW_PID ==="
    echo "=== Đang chờ Spring Boot khởi động... ==="


    # -------------------------------------------------------
    # WAIT FOR SERVICE
    # -------------------------------------------------------

    STARTED=false


    for i in {1..30}; do

        sleep 1


        if is_port_in_use; then

            STARTED=true

            break

        fi


        if ! ps -p "$NEW_PID" > /dev/null 2>&1; then

            break

        fi

    done


    # -------------------------------------------------------
    # START SUCCESS
    # -------------------------------------------------------

    if [ "$STARTED" = true ]; then

        echo ""
        echo "========================================================="
        echo "          BACKEND STARTED SUCCESSFULLY"
        echo "========================================================="
        echo "Service : $SERVICE_NAME"
        echo "PID     : $NEW_PID"
        echo "Port    : $SERVICE_PORT"
        echo "Log     : $LOG_FILE"
        echo "========================================================="
        echo ""

    else

        echo ""
        echo "========================================================="
        echo "             BACKEND START FAILED"
        echo "========================================================="
        echo ""

        echo "=== Log gần nhất: ==="
        echo ""

        if [ -f "$LOG_FILE" ]; then
            tail -n 50 "$LOG_FILE"
        fi


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
    echo "                  STOP BACKEND SERVICE"
    echo "========================================================="
    echo ""
    echo "Service : $SERVICE_NAME"
    echo "Port    : $SERVICE_PORT"
    echo ""


    stop_log_rotator


    # -------------------------------------------------------
    # STOP BY PID
    # -------------------------------------------------------

    if [ -f "$PID_FILE" ]; then

        PID=$(cat "$PID_FILE")


        if [ -n "$PID" ] && \
           ps -p "$PID" > /dev/null 2>&1; then

            echo "=== Đang dừng '$SERVICE_NAME' ==="
            echo "=== PID: $PID ==="


            # TERM toàn bộ process tree
            kill_process_tree "$PID" "TERM"


            echo "=== Đang chờ service dừng... ==="


            for i in {1..10}; do

                if ! ps -p "$PID" > /dev/null 2>&1; then
                    break
                fi


                sleep 1

            done


            # ------------------------------------------------
            # FORCE KILL
            # ------------------------------------------------

            if ps -p "$PID" > /dev/null 2>&1; then

                echo "=== Service chưa dừng hẳn. ==="
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
    # CHECK PORT
    # -------------------------------------------------------

    if is_port_in_use; then

        echo ""
        echo "=== Phát hiện process vẫn đang chiếm port $SERVICE_PORT ==="


        cleanup_port


        if [ $? -ne 0 ]; then

            echo ""
            echo "=== LỖI: Không thể giải phóng port $SERVICE_PORT ==="

            show_port_process

            exit 1
        fi

    fi


    # -------------------------------------------------------
    # CLEANUP
    # -------------------------------------------------------

    rm -f "$PID_FILE"
    rm -f "$ROTATOR_PID_FILE"


    echo ""
    echo "========================================================="
    echo "        BACKEND SERVICE STOPPED SUCCESSFULLY"
    echo "========================================================="
    echo "Service : $SERVICE_NAME"
    echo "Port    : $SERVICE_PORT"
    echo "========================================================="
    echo ""
}


# =========================================================
# STATUS SERVICE
# =========================================================

status_service() {

    echo ""
    echo "========================================================="
    echo "                  BACKEND SERVICE STATUS"
    echo "========================================================="
    echo ""
    echo "Service : $SERVICE_NAME"
    echo "Port    : $SERVICE_PORT"
    echo ""


    # -------------------------------------------------------
    # SERVICE PID
    # -------------------------------------------------------

    if is_running; then

        PID=$(cat "$PID_FILE")

        echo "Status  : RUNNING"
        echo "PID     : $PID"

    else

        echo "Status  : STOPPED"

    fi


    # -------------------------------------------------------
    # PORT
    # -------------------------------------------------------

    echo ""


    if is_port_in_use; then

        PORT_PIDS=$(get_port_pids)

        echo "Port Status : IN USE"
        echo "Port PID    : $PORT_PIDS"

    else

        echo "Port Status : FREE"

    fi


    # -------------------------------------------------------
    # LOG
    # -------------------------------------------------------

    echo ""
    echo "Log     : $LOG_FILE"


    if [ -f "$LOG_FILE" ]; then

        LINE_COUNT=$(wc -l < "$LOG_FILE")

        echo "Log Lines: $LINE_COUNT / $MAX_LOG_LINES"

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
        echo "                    BACKEND LOGS"
        echo "========================================================="
        echo "Service: $SERVICE_NAME"
        echo "Port   : $SERVICE_PORT"
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
        echo "=== Đang restart '$SERVICE_NAME'... ==="
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