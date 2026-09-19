package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_history")
public class InspectionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;

    private String status;

    private Integer totalMonitors;

    private Integer successCount;

    private Integer failCount;

    private Integer durationMs;

    private Integer schemaChangeCount;

    private String reportContent;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
