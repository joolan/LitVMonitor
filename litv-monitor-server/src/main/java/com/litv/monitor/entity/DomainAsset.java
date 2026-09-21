package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("domain_asset")
public class DomainAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String domain;

    private String ipAddress;

    private Integer port;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;

    private Integer monitorCount;

    private Boolean isAlive;

    private Boolean sslAlertEnabled;

    private String sslAlertConfigIds;

    private Integer sslAlertDaysBefore;

    private Boolean sslAlertOneDayBefore;

    private Boolean sslAlertOnExecute;

    private Boolean starred;

    @TableField(exist = false)
    private Integer sslRemainingDays;

    @TableField(exist = false)
    private String sslStatus;

    @TableField(exist = false)
    private LocalDateTime sslNotAfter;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
