package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("global_variable")
public class GlobalVariable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String value;

    private String description;

    private Boolean isSecret;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
