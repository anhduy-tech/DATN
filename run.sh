#!/bin/bash

# Lấy đường dẫn thư mục gốc dự án
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load biến môi trường từ file .env nếu tồn tại
ENV_FILE="$PROJECT_DIR/.env"
if [ -f "$ENV_FILE" ]; then
    echo ">>> Đang load biến môi trường từ: $ENV_FILE"
    echo ">>> Nội dung .env:"
    cat "$ENV_FILE"
    echo ">>> Các biến môi trường sau khi load:"
    
    set -a  # Tự động export tất cả biến khi source
    source "$ENV_FILE"
    set +a
    
    env
    echo ">>> Đã load biến môi trường từ .env"
else
    echo "Không tìm thấy file .env tại: $ENV_FILE"
fi

JAR_FILE="$PROJECT_DIR/build/libs/lapxpert-0.0.1-SNAPSHOT.jar"
PYTHON_MAIN="$PROJECT_DIR/src/main/java/com/lapxpert/backend/chatbox/python/main.py"
VENV_DIR="$PROJECT_DIR/.venv"

# --- Chạy Python trước ---
echo "Đang thiết lập và khởi động ứng dụng Python..."

if [ ! -d "$VENV_DIR" ]; then
    echo "Không tìm thấy môi trường ảo Python tại: $VENV_DIR"
    echo "Vui lòng chạy 'build.sh' để tạo môi trường ảo trước."
    exit 1
fi

if [ ! -f "$PYTHON_MAIN" ]; then
    echo "Không tìm thấy file Python tại: $PYTHON_MAIN"
    exit 1
fi

# Kích hoạt môi trường ảo và chạy main.py
source "$VENV_DIR/bin/activate"
python "$PYTHON_MAIN" &
PYTHON_PID=$!
echo "Ứng dụng Python đã khởi động (PID: $PYTHON_PID)"

# --- Sau đó chạy Java ---
echo "☕ Đang khởi động ứng dụng Java..."

if [ ! -f "$JAR_FILE" ]; then
    echo "Không tìm thấy file JAR tại: $JAR_FILE"
    echo "Vui lòng chạy 'build.sh' để build ứng dụng Java trước."
    deactivate
    exit 1
fi

java -jar "$JAR_FILE" &
JAVA_PID=$!
echo "✅ Ứng dụng Java đã khởi động (PID: $JAVA_PID)"

# --- Ghi PID ra file ---
echo "$PYTHON_PID" > pids.txt
echo "$JAVA_PID" >> pids.txt

echo "Cả hai ứng dụng đã được khởi động thành công."
echo "Để dừng chúng, hãy chạy script stop.sh hoặc dùng lệnh kill với PID."
echo "PID đã được lưu tại: pids.txt"

# Đợi cả hai tiến trình kết thúc
wait $PYTHON_PID $JAVA_PID
