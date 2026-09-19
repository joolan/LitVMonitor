#!/bin/bash
# Start LitVMonitor backend
# Usage: edit DEPLOY path below, then run: sudo ./start.sh
set -e
DEPLOY=/opt/litvmonitor
cd "$DEPLOY"
mkdir -p logs
if pgrep -f "litv-monitor.*.jar" >/dev/null; then
  echo "backend already running"
else
  setsid bash -c "cd $DEPLOY && java -jar litv-monitor-*.jar --server.address=127.0.0.1 > logs/backend.log 2>&1"
  echo "backend starting on 127.0.0.1:8088"
fi
