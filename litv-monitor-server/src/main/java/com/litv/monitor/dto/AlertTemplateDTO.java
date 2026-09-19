package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlertTemplateDTO {
    private Long id;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    @NotBlank(message = "触发类型不能为空")
    private String triggerType;

    private String content;

    private Integer cooldownMinutes;

    private Boolean enabled;

    private Boolean rateLimitEnabled;

    private Integer rateLimitCount;

    private Boolean recoveryNotify;

    private Integer recoveryConsecutiveCount;

    private String fallbackChannelIds;
}
