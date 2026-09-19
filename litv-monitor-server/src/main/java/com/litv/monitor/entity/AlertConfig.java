package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_config")
public class AlertConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    private String config;

    private String alertTemplate;

    private Boolean enabled;

    private Integer cooldownMinutes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
