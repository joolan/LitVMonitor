package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("monitor_group")
public class MonitorGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    private Boolean running;

    private Long createdBy;

    @TableField(exist = false)
    private Integer monitorCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
