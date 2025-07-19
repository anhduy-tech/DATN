#!/bin/bash

# Tìm các tiến trình đang sử dụng cổng 8080
echo "Đang tìm các tiến trình trên cổng 8080..."

# Sử dụng lsof để tìm PID (cần cài lsof trên hệ thống)
PIDS=$(lsof -t -i :8080 2>/dev/null)

# Nếu lsof không khả dụng, thử dùng netstat (tùy chọn thay thế)
if [ -z "$PIDS" ] && command -v netstat >/dev/null; then
    PIDS=$(netstat -tulnp 2>/dev/null | grep ":8080" | awk '{print $7}' | cut -d'/' -f1 | sort -u)
fi

# Kiểm tra xem có PID nào được tìm thấy không
if [ -z "$PIDS" ]; then
    echo "Không tìm thấy tiến trình nào đang sử dụng cổng 8080."
else
    echo "Đang dừng các tiến trình trên cổng 8080..."
    for pid in $PIDS; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo "Đã dừng PID: $pid"
        else
            echo "PID $pid không tồn tại hoặc đã dừng từ trước."
        fi
    done
fi

# Xóa file pids.txt nếu tồn tại
if [ -f pids.txt ]; then
    rm -f pids.txt
    echo "Đã xóa file pids.txt."
else
    echo "Không tìm thấy file pids.txt."
fi

echo "✅ Tất cả tiến trình trên cổng 8080 đã được xử lý."