package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.mapper.ExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExecutionLogService {

    private final ExecutionLogMapper executionLogMapper;

    public Page<ExecutionLog> listLogs(Page<ExecutionLog> page, Long monitorId, Long groupId, String domain, String ipAddress, String status, String schemaCheckStatus, String startTime, String endTime) {
        LambdaQueryWrapper<ExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (monitorId != null) {
            wrapper.eq(ExecutionLog::getMonitorId, monitorId);
        }
        if (groupId != null) {
            wrapper.eq(ExecutionLog::getGroupId, groupId);
        }
        if (domain != null && !domain.isEmpty()) {
            wrapper.like(ExecutionLog::getDomain, domain);
        }
        if (ipAddress != null && !ipAddress.isEmpty()) {
            wrapper.like(ExecutionLog::getIpAddress, ipAddress);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ExecutionLog::getStatus, status);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(ExecutionLog::getExecutedAt, startTime.replace("T", " "));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(ExecutionLog::getExecutedAt, endTime.replace("T", " "));
        }
        if (schemaCheckStatus != null && !schemaCheckStatus.isEmpty()) {
            String[] types = schemaCheckStatus.split(",");
            if (types.length == 1) {
                String t = types[0].trim();
                if ("hasChange".equals(t)) {
                    wrapper.isNotNull(ExecutionLog::getSchemaCheckStatus)
                        .and(w -> w.notIn(ExecutionLog::getSchemaCheckStatus, "未启用", "未配置Schema", "非JSON响应", "无响应体", "无变更", "无匹配变更", "")
                            .or().like(ExecutionLog::getSchemaCheckStatus, ","));
                } else if ("noChange".equals(t)) {
                    wrapper.eq(ExecutionLog::getSchemaCheckStatus, "无变更");
                } else if ("checked".equals(t)) {
                    wrapper.isNotNull(ExecutionLog::getSchemaCheckStatus)
                        .notIn(ExecutionLog::getSchemaCheckStatus, "未启用", "未配置Schema", "非JSON响应", "无响应体");
                } else {
                    wrapper.like(ExecutionLog::getSchemaCheckStatus, t);
                }
            } else {
                wrapper.and(w -> {
                    for (int i = 0; i < types.length; i++) {
                        String t = types[i].trim();
                        if (i > 0) w.or();
                        if ("hasChange".equals(t)) {
                            w.isNotNull(ExecutionLog::getSchemaCheckStatus)
                                .and(inner -> inner.notIn(ExecutionLog::getSchemaCheckStatus, "未启用", "未配置Schema", "非JSON响应", "无响应体", "无变更", "无匹配变更", "")
                                    .or().like(ExecutionLog::getSchemaCheckStatus, ","));
                        } else if ("noChange".equals(t)) {
                            w.eq(ExecutionLog::getSchemaCheckStatus, "无变更");
                        } else if ("checked".equals(t)) {
                            w.isNotNull(ExecutionLog::getSchemaCheckStatus)
                                .notIn(ExecutionLog::getSchemaCheckStatus, "未启用", "未配置Schema", "非JSON响应", "无响应体");
                        } else {
                            w.like(ExecutionLog::getSchemaCheckStatus, t);
                        }
                    }
                });
            }
        }
        wrapper.orderByDesc(ExecutionLog::getExecutedAt);
        return executionLogMapper.selectPage(page, wrapper);
    }

    public ExecutionLog getLogById(Long id) {
        return executionLogMapper.selectById(id);
    }

    public boolean deleteLog(Long id) {
        return executionLogMapper.deleteById(id) > 0;
    }

    public int cleanupOldLogs(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return executionLogMapper.delete(
                new LambdaQueryWrapper<ExecutionLog>()
                        .lt(ExecutionLog::getExecutedAt, cutoff)
        );
    }

}
