package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reminder_task")
public class ReminderTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String title;

    private String description;

    private String category;

    private String dueDate;

    private String recurrenceType;

    private String recurrenceConfig;

    private Boolean advanceEnabled;

    private Integer advanceMinutes;

    private Integer advanceDays;

    private String alertChannelIds;

    private Boolean enabled;

    private Boolean completed;

    private String lastRemindedAt;

    private String lastSnoozedAt;

    private String nextDueAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
