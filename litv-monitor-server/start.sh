#!/bin/bash
cd "$(dirname "$0")"
if [ -n "$JAVA_HOME" ]; then
    "$JAVA_HOME/bin/java" -jar target/litv-monitor-1.3.1.jar > stdout.log 2>&1
else
    java -jar target/litv-monitor-1.3.1.jar > stdout.log 2>&1
fi
