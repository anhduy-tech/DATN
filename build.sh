#!/bin/bash

# Build Java application
echo "Building Java application..."
./gradlew bootJar

if [ $? -ne 0 ]; then
    echo "Java build failed. Exiting."
    exit 1
fi

echo "Java application built successfully."

# Build Python environment
echo "Setting up Python environment..."
PYTHON_DIR="/home/kumduy/Desktop/DATN/lapxpert-backend-2/src/main/java/com/lapxpert/backend/chatbox/python"
VENV_DIR="$PYTHON_DIR/venv"

# Create a virtual environment if it doesn't exist
if [ ! -d "$VENV_DIR" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv "$VENV_DIR"
    if [ $? -ne 0 ]; then
        echo "Failed to create virtual environment. Exiting."
        exit 1
    fi
fi

# Activate the virtual environment and install dependencies
echo "Activating virtual environment and installing Python dependencies..."
source "$VENV_DIR/bin/activate"
pip install -r "$PYTHON_DIR/requirements.txt"

if [ $? -ne 0 ]; then
    echo "Failed to install Python dependencies. Exiting."
    exit 1
fi

echo "Python environment set up successfully."
echo "Build process completed."