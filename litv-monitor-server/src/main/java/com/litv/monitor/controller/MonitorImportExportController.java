package com.litv.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.dto.MonitorDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.AlertChannel;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.mapper.AlertChannelMapper;
import com.litv.monitor.mapper.MonitorMapper;
import com.litv.monitor.service.AuditLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;

@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorImportExportController {

    private final MonitorMapper monitorMapper;
    private final AlertChannelMapper alertChannelMapper;
    private final AuditLogService auditLogService;

    private static final String[][] EXPORT_COLUMNS = {
        {"名称", "name"},
        {"描述", "description"},
        {"监控类型", "monitorType"},
        {"URL/地址", "url"},
        {"请求方法", "method"},
        {"请求头", "headers"},
        {"请求体", "body"},
        {"请求体类型", "bodyType"},
        {"期望状态码", "expectedStatus"},
        {"期望文本", "expectedText"},
        {"期望正则", "expectedRegex"},
        {"JSONPath", "jsonPath"},
        {"JSON期望值", "jsonExpected"},
        {"期望Schema", "expectedSchemaJson"},
        {"超时(秒)", "timeout"},
        {"重试次数", "retryCount"},
        {"启用", "enabled"},
        {"响应时间阈值(ms)", "responseTimeThreshold"},
        {"响应时间连续次数", "responseTimeConsecutiveCount"},
        {"响应时间告警", "responseTimeAlertEnabled"},
        {"响应时间告警渠道ID", "responseTimeAlertConfigIds"},
        {"变量提取配置", "variableExtractConfig"},
        {"最大响应体大小", "maxResponseBodySize"},
        {"签名类型", "signType"},
        {"签名配置", "signConfig"},
        {"签名目标", "signTarget"},
        {"签名字段名", "signFieldName"},
        {"预请求脚本", "preRequestScript"},
        {"公开状态页", "showOnStatusPage"},
        {"Schema告警", "schemaAlertEnabled"},
        {"Schema变更类型", "schemaAlertChangeTypes"},
        {"Schema告警渠道ID", "schemaAlertChannelIds"},
        {"告警启用", "alertEnabled"},
        {"告警连续次数", "alertConsecutiveCount"},
        {"告警渠道ID", "alertConfigIds"},
        {"配置(JSON)", "config"},
    };

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public void exportMonitors(HttpServletResponse response) {
        try {
            List<Monitor> monitors = monitorMapper.selectList(
                new LambdaQueryWrapper<Monitor>().orderByDesc(Monitor::getCreatedAt));

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = URLEncoder.encode("监控项列表.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("监控项");

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < EXPORT_COLUMNS.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_COLUMNS[i][0]);
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < monitors.size(); r++) {
                Monitor m = monitors.get(r);
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                for (int c = 0; c < EXPORT_COLUMNS.length; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                    Object val = getFieldValue(m, EXPORT_COLUMNS[c][1]);
                    if (val == null) {
                        cell.setCellValue("");
                    } else if (val instanceof Boolean) {
                        cell.setCellValue((Boolean) val ? "是" : "否");
                    } else if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                    } else {
                        cell.setCellValue(val.toString());
                    }
                }
            }

            for (int i = 0; i < Math.min(10, EXPORT_COLUMNS.length); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(response.getOutputStream());
            workbook.close();

            String username = getCurrentUsername();
            auditLogService.record(getCurrentUserId(), username, "EXPORT", "MONITOR",
                null, null, "导出监控项 " + monitors.size() + " 条", null);
            log.info("Exported {} monitors to Excel", monitors.size());
        } catch (Exception e) {
            log.error("Failed to export monitors", e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> importMonitors(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "alertChannelOverride", defaultValue = "false") boolean alertChannelOverride,
            @RequestParam(value = "alertChannelIds", required = false) String alertChannelIds) {
        try {
            if (file.isEmpty()) {
                return Result.error("上传文件为空");
            }
            String name = file.getOriginalFilename();
            if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls"))) {
                return Result.error("仅支持 .xlsx 或 .xls 文件");
            }

            List<MonitorDTO> dtos = parseExcel(file.getInputStream());
            if (dtos.isEmpty()) {
                return Result.error("Excel 中没有有效的监控项数据");
            }

            List<String> errors = new ArrayList<>();
            for (int i = 0; i < dtos.size(); i++) {
                MonitorDTO dto = dtos.get(i);
                int row = i + 2;
                if (StrUtil.isBlank(dto.getName())) {
                    errors.add("第 " + row + " 行：名称不能为空");
                }
                if (StrUtil.isBlank(dto.getUrl())) {
                    errors.add("第 " + row + " 行：URL/地址不能为空");
                }
            }
            if (!errors.isEmpty()) {
                return Result.error("校验失败：\n" + String.join("\n", errors));
            }

            Set<Long> validChannelIds = getAllValidChannelIds();

            for (MonitorDTO dto : dtos) {
                if (alertChannelOverride) {
                    dto.setAlertConfigIds(alertChannelIds);
                    dto.setResponseTimeAlertConfigIds(null);
                    dto.setSchemaAlertChannelIds(null);
                } else {
                    dto.setAlertConfigIds(validateChannelIds(dto.getAlertConfigIds(), validChannelIds));
                    dto.setResponseTimeAlertConfigIds(validateChannelIds(dto.getResponseTimeAlertConfigIds(), validChannelIds));
                    dto.setSchemaAlertChannelIds(validateChannelIds(dto.getSchemaAlertChannelIds(), validChannelIds));
                }
            }

            int count = 0;
            for (MonitorDTO dto : dtos) {
                Monitor monitor = new Monitor();
                BeanUtils.copyProperties(dto, monitor);
                monitorMapper.insert(monitor);
                count++;
            }

            String username = getCurrentUsername();
            auditLogService.record(getCurrentUserId(), username, "IMPORT", "MONITOR",
                null, null, "导入监控项 " + count + " 条", null);

            log.info("Imported {} monitors from Excel", count);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", count);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to import monitors", e);
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }

    private Set<Long> getAllValidChannelIds() {
        return alertChannelMapper.selectList(
            new LambdaQueryWrapper<AlertChannel>().eq(AlertChannel::getEnabled, true)
        ).stream().map(AlertChannel::getId).collect(Collectors.toSet());
    }

    private String validateChannelIds(String ids, Set<Long> validIds) {
        if (StrUtil.isBlank(ids)) return null;
        return Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .filter(s -> {
                try { return validIds.contains(Long.parseLong(s)); }
                catch (NumberFormatException e) { return false; }
            })
            .collect(Collectors.joining(","));
    }

    private List<MonitorDTO> parseExcel(InputStream inputStream) {
        List<MonitorDTO> result = new ArrayList<>();

        ExcelReader reader = ExcelUtil.getReader(inputStream);

        List<Object> headerRow = reader.readRow(0);
        if (headerRow == null || headerRow.isEmpty()) return result;

        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            Object cell = headerRow.get(i);
            if (cell != null) {
                String val = cell.toString().trim();
                if (!val.isEmpty()) {
                    headerMap.put(val, i);
                }
            }
        }

        Map<String, String> colToField = new HashMap<>();
        for (String[] col : EXPORT_COLUMNS) {
            if (headerMap.containsKey(col[0])) {
                colToField.put(col[0], col[1]);
            }
        }

        List<List<Object>> dataRows = reader.read(1);
        if (dataRows == null) return result;

        for (List<Object> row : dataRows) {
            if (row == null || row.isEmpty()) continue;

            boolean allEmpty = true;
            for (Object cell : row) {
                if (cell != null && !cell.toString().trim().isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) continue;

            MonitorDTO dto = new MonitorDTO();
            for (Map.Entry<String, String> entry : colToField.entrySet()) {
                Integer colIdx = headerMap.get(entry.getKey());
                if (colIdx == null || colIdx >= row.size()) continue;
                Object cellVal = row.get(colIdx);
                setDtoField(dto, entry.getValue(), cellVal);
            }
            result.add(dto);
        }
        return result;
    }

    private void setDtoField(MonitorDTO dto, String field, Object cellVal) {
        try {
            String strVal = cellVal != null ? cellVal.toString().trim() : "";
            switch (field) {
                case "name" -> dto.setName(StrUtil.emptyToNull(strVal));
                case "description" -> dto.setDescription(StrUtil.emptyToNull(strVal));
                case "monitorType" -> dto.setMonitorType(StrUtil.emptyToNull(strVal));
                case "url" -> dto.setUrl(StrUtil.emptyToNull(strVal));
                case "method" -> dto.setMethod(StrUtil.emptyToNull(strVal));
                case "headers" -> dto.setHeaders(StrUtil.emptyToNull(strVal));
                case "body" -> dto.setBody(StrUtil.emptyToNull(strVal));
                case "bodyType" -> dto.setBodyType(StrUtil.emptyToNull(strVal));
                case "expectedText" -> dto.setExpectedText(StrUtil.emptyToNull(strVal));
                case "expectedRegex" -> dto.setExpectedRegex(StrUtil.emptyToNull(strVal));
                case "jsonPath" -> dto.setJsonPath(StrUtil.emptyToNull(strVal));
                case "jsonExpected" -> dto.setJsonExpected(StrUtil.emptyToNull(strVal));
                case "expectedSchemaJson" -> dto.setExpectedSchemaJson(StrUtil.emptyToNull(strVal));
                case "variableExtractConfig" -> dto.setVariableExtractConfig(StrUtil.emptyToNull(strVal));
                case "signType" -> dto.setSignType(StrUtil.emptyToNull(strVal));
                case "signConfig" -> dto.setSignConfig(StrUtil.emptyToNull(strVal));
                case "signTarget" -> dto.setSignTarget(StrUtil.emptyToNull(strVal));
                case "signFieldName" -> dto.setSignFieldName(StrUtil.emptyToNull(strVal));
                case "preRequestScript" -> dto.setPreRequestScript(StrUtil.emptyToNull(strVal));
                case "schemaAlertChangeTypes" -> dto.setSchemaAlertChangeTypes(StrUtil.emptyToNull(strVal));
                case "schemaAlertChannelIds" -> dto.setSchemaAlertChannelIds(StrUtil.emptyToNull(strVal));
                case "alertConfigIds" -> dto.setAlertConfigIds(StrUtil.emptyToNull(strVal));
                case "responseTimeAlertConfigIds" -> dto.setResponseTimeAlertConfigIds(StrUtil.emptyToNull(strVal));
                case "config" -> dto.setConfig(StrUtil.emptyToNull(strVal));
                case "expectedStatus" -> dto.setExpectedStatus(parseInteger(strVal));
                case "timeout" -> dto.setTimeout(parseInteger(strVal));
                case "retryCount" -> dto.setRetryCount(parseInteger(strVal));
                case "responseTimeThreshold" -> dto.setResponseTimeThreshold(parseInteger(strVal));
                case "responseTimeConsecutiveCount" -> dto.setResponseTimeConsecutiveCount(parseInteger(strVal));
                case "maxResponseBodySize" -> dto.setMaxResponseBodySize(parseInteger(strVal));
                case "alertConsecutiveCount" -> dto.setAlertConsecutiveCount(parseInteger(strVal));
                case "enabled" -> dto.setEnabled(parseBoolean(strVal));
                case "responseTimeAlertEnabled" -> dto.setResponseTimeAlertEnabled(parseBoolean(strVal));
                case "showOnStatusPage" -> dto.setShowOnStatusPage(parseBoolean(strVal));
                case "schemaAlertEnabled" -> dto.setSchemaAlertEnabled(parseBoolean(strVal));
                case "alertEnabled" -> dto.setAlertEnabled(parseBoolean(strVal));
            }
        } catch (Exception e) {
            log.debug("Failed to set field {} from cell: {}", field, e.getMessage());
        }
    }

    private Integer parseInteger(String str) {
        if (StrUtil.isBlank(str)) return null;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String str) {
        if (StrUtil.isBlank(str)) return null;
        return "是".equals(str) || "true".equalsIgnoreCase(str) || "1".equals(str);
    }

    private Object getFieldValue(Monitor monitor, String fieldName) {
        try {
            var field = Monitor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(monitor);
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private Long getCurrentUserId() {
        return null;
    }
}
