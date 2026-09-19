package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("group_monitor")
public class GroupMonitor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long monitorId;

    private Integer sortOrder;

    private Boolean continueOnFail;

    private Boolean isGroupStart;

    private String variableScope;

    @TableField(exist = false)
    private String monitorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
