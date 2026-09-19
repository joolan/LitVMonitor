package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_rate_limit")
public class AlertRateLimit {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String fingerprint;

    private Integer currentCount;

    private LocalDateTime windowStart;

    private Boolean lastAlertSuccess;

    private Integer consecutiveSuccessCount; // 连续成功次数

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
