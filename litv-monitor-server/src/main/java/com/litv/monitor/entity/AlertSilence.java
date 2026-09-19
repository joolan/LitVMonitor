package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_silence")
public class AlertSilence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String silenceType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String cronExpression;
    private String scheduleConfig;
    private String applyTo;
    private String applyIds;
    private Boolean enabled;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
