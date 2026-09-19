#!/bin/bash
APP_NAME="litv-monitor-1.0.0.jar"
PID=$(pgrep -f "$APP_NAME")
if [ -n "$PID" ]; then
    kill "$PID"
    echo "Stopped (PID: $PID)"
else
    echo "Not running"
fi
