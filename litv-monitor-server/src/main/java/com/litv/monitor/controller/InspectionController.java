package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.InspectionConfig;
import com.litv.monitor.entity.InspectionDetail;
import com.litv.monitor.entity.InspectionHistory;
import com.litv.monitor.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inspection")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping("/config")
    public Result<List<InspectionConfig>> listConfigs() {
        return Result.success(inspectionService.listConfigs());
    }

    @GetMapping("/config/{id}")
    public Result<InspectionConfig> getConfig(@PathVariable Long id) {
        return Result.success(inspectionService.getConfig(id));
    }

    @PostMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<InspectionConfig> createConfig(@RequestBody InspectionConfig config) {
        return Result.success(inspectionService.createConfig(config));
    }

    @PutMapping("/config/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<InspectionConfig> updateConfig(@PathVariable Long id, @RequestBody InspectionConfig config) {
        config.setId(id);
        return Result.success(inspectionService.updateConfig(config));
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        inspectionService.deleteConfig(id);
        return Result.success();
    }

    @GetMapping("/history/{configId}")
    public Result<List<InspectionHistory>> listHistory(@PathVariable Long configId) {
        return Result.success(inspectionService.listHistory(configId));
    }

    @GetMapping("/history/detail/{id}")
    public Result<InspectionHistory> getHistoryById(@PathVariable Long id) {
        return Result.success(inspectionService.getHistoryById(id));
    }

    @GetMapping("/history/{historyId}/details")
    public Result<List<InspectionDetail>> getHistoryDetails(@PathVariable Long historyId) {
        return Result.success(inspectionService.getHistoryDetails(historyId));
    }

    @PostMapping("/run/{configId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> runInspection(@PathVariable Long configId) {
        if (!inspectionService.tryStartInspection()) {
            throw new IllegalStateException("巡检执行中，请稍后重试");
        }
        inspectionService.runInspection(configId);
        return Result.success();
    }
}
