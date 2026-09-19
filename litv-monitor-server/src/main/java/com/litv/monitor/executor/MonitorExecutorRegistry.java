package com.litv.monitor.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MonitorExecutorRegistry {

    private final Map<String, MonitorExecutor> executorMap = new ConcurrentHashMap<>();

    public MonitorExecutorRegistry(List<MonitorExecutor> executors) {
        for (MonitorExecutor executor : executors) {
            executorMap.put(executor.getMonitorType(), executor);
            log.info("Registered monitor executor: {} -> {}", executor.getMonitorType(), executor.getClass().getSimpleName());
        }
    }

    public MonitorExecutor getExecutor(String type) {
        return executorMap.get(type);
    }
}
