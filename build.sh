#!/bin/bash

# Get absolute path to the project root (the folder containing this script)
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$PROJECT_DIR/.venv"
REQ_FILE="$PROJECT_DIR/src/main/java/com/lapxpert/backend/chatbox/python/requirements.txt"

echo "Tìm thư mục dự án: $PROJECT_DIR"

# Build Java application
echo "Xây dựng ứng dụng Java..."
"$PROJECT_DIR/gradlew" bootJar

if [ $? -ne 0 ]; then
    echo "Xây dựng ứng dụng Java thất bại. Vui lòng kiểm tra lỗi."
    exit 1
fi

echo "Xây dựng môi trường java thành công."

echo "Thiết lập môi trường Python..."

if [ ! -d "$VENV_DIR" ]; then
    echo "Tạo môi trương python tại $VENV_DIR..."
    python3 -m venv "$VENV_DIR"
    if [ $? -ne 0 ]; then
        echo "Tạo môi trường ảo thất bại. Đang thoát."
        exit 1
    fi
else
    echo "Môi trường ảo đã tồn tại tại $VENV_DIR"
fi

echo "Kích hoạt môi trường ảo..."
source "$VENV_DIR/bin/activate"

if [ ! -f "$REQ_FILE" ]; then
    echo "Không tìm thấy requirements.txt tại: $REQ_FILE"
    deactivate
    exit 1
fi

echo "Cài đặt các phụ thuộc Python từ $REQ_FILE..."
pip install -r "$REQ_FILE"

if [ $? -ne 0 ]; then
    echo "Không thể cài đặt các phụ thuộc Python."
    deactivate
    exit 1
fi

echo "Thiết lập môi trường Python thành công."
echo "Quá trình xây dựng hoàn tất."

deactivate
echo "Môi trường ảo đã bị vô hiệu hóa."
echo "Bạn có thể chạy ứng dụng Java và các tập lệnh Python theo nhu cầu."