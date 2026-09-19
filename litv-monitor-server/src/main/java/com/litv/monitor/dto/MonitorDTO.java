package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MonitorDTO {

    private Long id;

    @NotBlank(message = "监控名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "监控URL不能为空")
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

    private Integer responseTimeThreshold;

    private Integer responseTimeConsecutiveCount;

    private Boolean responseTimeAlertEnabled;

    private String responseTimeAlertConfigIds;

    private String expectedRegex;

    private Integer maxResponseBodySize;

    private String signType;

    private String signConfig;

    private String signTarget;

    private String signFieldName;

    private String preRequestScript;

    private Boolean schemaAlertEnabled;

    private String expectedSchemaJson;

    private String schemaAlertChangeTypes;

    private String schemaAlertChannelIds;

    private Boolean showOnStatusPage;

    private Boolean alertEnabled;

    private Integer alertConsecutiveCount;

    private String alertConfigIds;

    private String monitorType;

    private String config;
}
