package com.litv.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverview {

    private Long monitorEnabledCount;
    private Long monitorDisabledCount;
    private Long groupEnabledCount;
    private Long groupDisabledCount;
    private Long totalDomains;
    private Long sslExpiringCount;
    private Long sslExpiredCount;
    private Long sslMismatchCount;
    private Map<String, Long> responseTimeoutStats; // {"10m": N, "1h": N, "6h": N}
    private List<Map<String, Object>> alertTrend;          // [{time, count}, ...] last 24h hourly
    private List<Map<String, Object>> alertTypeDistribution; // [{type, count}, ...]
}
