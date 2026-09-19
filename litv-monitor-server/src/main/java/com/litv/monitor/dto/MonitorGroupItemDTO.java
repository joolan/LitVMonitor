package com.litv.monitor.dto;

import lombok.Data;

@Data
public class MonitorGroupItemDTO {

    private Long monitorId;

    private Integer sortOrder;

    private Boolean continueOnFail;

    private Boolean isGroupStart;

    private String variableScope;
}
