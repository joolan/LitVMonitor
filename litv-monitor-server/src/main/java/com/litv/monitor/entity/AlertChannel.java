package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_config")
public class AlertChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;       // EMAIL or WEBHOOK
    private String config;     // JSON string
    private Boolean enabled;
    private LocalDateTime createdAt;
}
