#!/bin/bash
# Stop LitVMonitor backend
pkill -f "litv-monitor.*.jar" && echo "backend stopped" || echo "backend not running"
