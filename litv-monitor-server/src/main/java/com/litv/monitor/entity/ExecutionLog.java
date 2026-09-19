package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("execution_log")
public class ExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long monitorId;

    private String monitorName;

    private Long groupId;

    private String executionId;

    private String url;

    private String domain;

    private String ipAddress;

    private String status;

    private Integer statusCode;

    private Integer responseTime;

    private String responseBody;

    private String requestHeaders;

    private String requestBody;

    private String errorMessage;

    private String variableReferences;

    private String variableSettings;

    private String schemaCheckStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime executedAt;
}
