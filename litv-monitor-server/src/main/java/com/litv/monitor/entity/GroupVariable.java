package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("group_variable")
public class GroupVariable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String name;

    private String value;

    private Long sourceMonitorId;

    private String sourceJsonPath;

    private String scope;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
