#!/bin/bash

if [ ! -f pids.txt ]; then
    echo "Không tìm thấy file pids.txt. Không thể dừng tiến trình."
    exit 1
fi

echo "Đang dừng các tiến trình..."

while read pid; do
    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid"
        echo "Đã dừng PID: $pid"
    else
        echo "PID $pid không tồn tại hoặc đã dừng từ trước."
    fi
done < pids.txt

rm -f pids.txt
echo "✅ Tất cả tiến trình đã được xử lý."

