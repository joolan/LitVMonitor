package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String targetType;
    private String targetId;
    private String targetName;
    private String detail;
    private String requestBody;
    private String requestUrl;
    private String ipAddress;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
