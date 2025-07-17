#!/bin/bash

PID_FILE="pids.txt"

if [ -f "$PID_FILE" ]; then
    PIDS=$(cat "$PID_FILE")
    echo "Stopping processes with PIDs: $PIDS"
    kill $PIDS
    rm "$PID_FILE"
    echo "Processes stopped and PID file removed."
else
    echo "No PID file found. Applications might not be running or were stopped manually."
fi