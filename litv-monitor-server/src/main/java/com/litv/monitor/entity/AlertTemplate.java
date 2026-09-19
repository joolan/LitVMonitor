package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_template")
public class AlertTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String triggerType;  // FAIL, RESPONSE_TIME, GROUP_FAIL, SSL_CERT, ALL
    private String content;      // Template content with {{var}} placeholders
    private Integer cooldownMinutes; // Default 30
    private Boolean enabled;
    private Boolean rateLimitEnabled;
    private Integer rateLimitCount;
    private Boolean recoveryNotify;
    private Integer recoveryConsecutiveCount; // 连续正常次数才发送恢复通知
    private String fallbackChannelIds; // 兜底告警渠道
    private String templateType; // SYSTEM=系统预置, CUSTOM=用户自定义
    private LocalDateTime createdAt;
}
