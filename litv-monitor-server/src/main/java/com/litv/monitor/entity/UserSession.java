package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_session")
public class UserSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String jti;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime loginAt;

    private LocalDateTime lastAccessAt;

    private Boolean active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
