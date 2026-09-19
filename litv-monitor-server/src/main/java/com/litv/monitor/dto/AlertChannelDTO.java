package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlertChannelDTO {
    private Long id;

    @NotBlank(message = "渠道名称不能为空")
    private String name;

    @NotBlank(message = "渠道类型不能为空")
    private String type;

    @NotBlank(message = "配置内容不能为空")
    private String config;

    private Boolean enabled;
}
