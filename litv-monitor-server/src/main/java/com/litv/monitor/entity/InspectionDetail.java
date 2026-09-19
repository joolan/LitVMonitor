package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_detail")
public class InspectionDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long historyId;

    private Long monitorId;

    private String monitorName;

    private String monitorUrl;

    private String status;

    private Integer responseTime;

    private Integer statusCode;

    private String errorMessage;

    private String schemaChangeInfo;

    private LocalDateTime createdAt;
}
