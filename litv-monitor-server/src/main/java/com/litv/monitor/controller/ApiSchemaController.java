package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.ApiSchema;
import com.litv.monitor.service.ApiSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api-schema")
@RequiredArgsConstructor
public class ApiSchemaController {

    private final ApiSchemaService schemaService;

    @GetMapping("/list")
    public Result<List<ApiSchema>> listAll() {
        return Result.success(schemaService.listAll());
    }

    @GetMapping("/{id}")
    public Result<ApiSchema> getById(@PathVariable Long id) {
        return Result.success(schemaService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<ApiSchema> create(@RequestBody ApiSchema schema) {
        return Result.success(schemaService.create(schema));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<ApiSchema> update(@PathVariable Long id, @RequestBody ApiSchema schema) {
        schema.setId(id);
        return Result.success(schemaService.update(schema));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        schemaService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/history")
    public Result<?> getHistory(@PathVariable Long id) {
        return Result.success(schemaService.getHistory(id));
    }

    @GetMapping("/history/{historyId}/compare")
    public Result<?> compareVersions(@PathVariable Long historyId) {
        return Result.success(schemaService.compareVersions(historyId));
    }

    @PostMapping("/compare")
    public Result<?> compareSchema(@RequestBody Map<String, String> body) {
        String oldSchema = body.get("oldSchema");
        String newSchema = body.get("newSchema");
        return Result.success(schemaService.compareSchemaJson(oldSchema, newSchema));
    }

    @PostMapping("/{id}/validate")
    public Result<Map<String, Object>> validate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ApiSchema schema = schemaService.getById(id);
        if (schema == null) {
            return Result.error(404, "Schema not found");
        }
        return Result.success(schemaService.validateResponse(schema, body.get("responseJson")));
    }

    @PostMapping("/{id}/check")
    public Result<Void> checkChanges(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ApiSchema schema = schemaService.getById(id);
        if (schema == null) {
            return Result.error(404, "Schema not found");
        }
        schemaService.checkSchemaChanges(schema, body.get("responseJson"));
        return Result.success();
    }

    @GetMapping("/history/all")
    public Result<List<Map<String, Object>>> listAllHistory(
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) String changeType) {
        return Result.success(schemaService.listAllHistory(hours, changeType));
    }
}
