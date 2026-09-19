package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class MonitorGroupDTO {

    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String description;

    private String cronExpression;

    private String scheduleType;

    private String scheduleConfig;

    private Integer retryInterval;

    private Boolean enabled;

    private Boolean alertOnFail;

    private Integer failThreshold;

    private String alertConfigIds;

    private String failCriteriaType;

    private Integer failCountThreshold;

    private Integer failPercentThreshold;

    private List<MonitorGroupItemDTO> monitors;
}
