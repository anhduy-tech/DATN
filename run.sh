#!/bin/bash

# Start Java application in the background
echo "Starting Java application..."
java -jar build/libs/lapxpert-0.0.1-SNAPSHOT.jar &
JAVA_PID=$!
echo "Java application started with PID: $JAVA_PID"

# Set up and run Python application
echo "Setting up and running Python application..."
PYTHON_DIR="/home/kumduy/Desktop/DATN/lapxpert-backend-2/src/main/java/com/lapxpert/backend/chatbox/python"
VENV_DIR="$PYTHON_DIR/venv"

if [ ! -d "$VENV_DIR" ]; then
    echo "Python virtual environment not found. Please run build.sh first."
    exit 1
fi

source "$VENV_DIR/bin/activate"
python "$PYTHON_DIR/main.py" &
PYTHON_PID=$!
echo "Python application started with PID: $PYTHON_PID"

echo "Both Java and Python applications are running."
echo "To stop them, use 'stop.sh' or manually kill processes with PIDs: $JAVA_PID, $PYTHON_PID"

echo "$JAVA_PID" > pids.txt
echo "$PYTHON_PID" >> pids.txt

wait $JAVA_PID $PYTHON_PID