package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("domain_ip_history")
public class DomainIpHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String domain;

    private String ipAddress;

    private Integer port;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;
}
