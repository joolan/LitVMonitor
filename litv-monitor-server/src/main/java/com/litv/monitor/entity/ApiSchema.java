package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_schema")
public class ApiSchema {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String schemaJson;

    private Boolean enabled;

    private String lastCheckStatus;

    private String lastCheckMessage;

    private LocalDateTime lastCheckedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
