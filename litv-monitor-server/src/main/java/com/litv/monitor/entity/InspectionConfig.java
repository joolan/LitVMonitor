package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_config")
public class InspectionConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String monitorIds;

    private String scheduleCron;

    private Boolean enabled;

    private LocalDateTime lastRunAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
