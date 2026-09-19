package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlertConfigDTO {

    private Long id;

    @NotBlank(message = "配置名称不能为空")
    private String name;

    @NotBlank(message = "告警类型不能为空")
    private String type;

    @NotBlank(message = "配置内容不能为空")
    private String config;

    private String alertTemplate;

    private Boolean enabled;

    private Integer cooldownMinutes;
}
