package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ssl_certificate")
public class SslCertificate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long domainAssetId;

    private String domain;

    private Integer port;

    private String issuer;

    private String subject;

    private String serialNumber;

    private LocalDateTime notBefore;

    private LocalDateTime notAfter;

    private Integer remainingDays;

    private String fingerprint;

    private Boolean isValid;

    private String status; // VALID / EXPIRED / MISMATCH

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime checkedAt;
}
