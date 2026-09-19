package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.AlertLog;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.mapper.AlertLogMapper;
import com.litv.monitor.mapper.ExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final ExecutionLogMapper executionLogMapper;
    private final AlertLogMapper alertLogMapper;

    @Value("${monitor.log-retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * ?")
    public void autoCleanup() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        try {
            int execDeleted = executionLogMapper.delete(
                    new LambdaQueryWrapper<ExecutionLog>().lt(ExecutionLog::getExecutedAt, cutoff));
            int alertDeleted = alertLogMapper.delete(
                    new LambdaQueryWrapper<AlertLog>().lt(AlertLog::getSentAt, cutoff));
            log.info("Auto retention: deleted {} execution logs and {} alert logs older than {} days",
                    execDeleted, alertDeleted, retentionDays);
        } catch (Exception e) {
            log.error("Auto retention cleanup failed", e);
        }
    }
}
