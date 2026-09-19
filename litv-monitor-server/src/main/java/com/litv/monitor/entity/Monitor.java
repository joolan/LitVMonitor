package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("monitor")
public class Monitor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String url;

    private String method;

    private String headers;

    private String body;

    private String bodyType;

    private Integer expectedStatus;

    private String expectedText;

    private String jsonPath;

    private String jsonExpected;

    private Integer timeout;

    private Integer retryCount;

    private Boolean enabled;

    private String variableExtractConfig;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer responseTimeThreshold;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer responseTimeConsecutiveCount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean responseTimeAlertEnabled;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String responseTimeAlertConfigIds;

    private String expectedRegex;

    private Integer maxResponseBodySize;

    private String signType;

    private String signConfig;

    private String signTarget;

    private String signFieldName;

    private String preRequestScript;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean showOnStatusPage;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean schemaAlertEnabled;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String expectedSchemaJson;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String schemaAlertChangeTypes;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String schemaAlertChannelIds;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean alertEnabled;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer alertConsecutiveCount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String alertConfigIds;

    private String monitorType;

    private String config;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
