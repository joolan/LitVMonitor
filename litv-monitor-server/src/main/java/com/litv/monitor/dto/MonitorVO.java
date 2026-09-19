package com.litv.monitor.dto;

import com.litv.monitor.entity.Monitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MonitorVO extends Monitor {

    private Long groupCount;
}
