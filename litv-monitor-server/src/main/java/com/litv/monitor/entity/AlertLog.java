package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_log")
public class AlertLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long monitorId;

    private String monitorName;

    private Long groupId;

    private String groupName;

    private String executionId;

    private Long alertConfigId;

    private String alertConfigName;

    private String alertType;

    private String triggerType;

    private Integer statusCode;

    private Integer responseTime;

    private String errorMessage;

    private String alertContent;

    private String status;

    private LocalDateTime sentAt;
}
