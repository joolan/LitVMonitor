package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;

import java.util.Map;

public interface MonitorExecutor {

    String getMonitorType();

    void execute(Monitor monitor, ExecutionLog log, Map<String, String> vars);
}
