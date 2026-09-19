package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReminderTaskDTO {

    private Long id;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotBlank(message = "到期日期不能为空")
    private String dueDate;

    @NotBlank(message = "重复类型不能为空")
    private String recurrenceType;

    private String recurrenceConfig;

    private Boolean advanceEnabled;

    private Integer advanceMinutes;

    private Integer advanceDays;

    private String alertChannelIds;

    private Boolean enabled;
}
