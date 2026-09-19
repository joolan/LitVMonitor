package com.litv.monitor.controller;

import com.litv.monitor.dto.DashboardOverview;
import com.litv.monitor.dto.Result;
import com.litv.monitor.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverview> getOverview() {
        return Result.success(dashboardService.getOverview());
    }

    @GetMapping("/url-trend")
    public Result<List<Map<String, Object>>> getUrlTrend(
            @RequestParam(defaultValue = "minute") String granularity) {
        return Result.success(dashboardService.getUrlResponseTimeTrend(granularity));
    }

    @GetMapping("/uptime")
    public Result<Map<String, Object>> getUptime(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "24") int hours) {
        return Result.success(dashboardService.getUptimeStats(monitorId, domain, hours));
    }

    @GetMapping("/schema-changes")
    public Result<Map<String, Object>> getSchemaChanges(
            @RequestParam(defaultValue = "24") int hours) {
        return Result.success(dashboardService.getSchemaCheckStats(hours));
    }
}
