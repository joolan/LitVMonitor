package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_schema_history")
public class ApiSchemaHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long schemaId;

    private String changeType;

    private String changeDescription;

    private String oldSchema;

    private String newSchema;

    private LocalDateTime createdAt;
}
