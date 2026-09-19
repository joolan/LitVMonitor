package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.litv.monitor.dto.SchemaChange;
import com.litv.monitor.entity.ApiSchema;
import com.litv.monitor.entity.ApiSchemaHistory;
import com.litv.monitor.mapper.ApiSchemaHistoryMapper;
import com.litv.monitor.mapper.ApiSchemaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSchemaService {

    private final ApiSchemaMapper schemaMapper;
    private final ApiSchemaHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<ApiSchema> listAll() {
        return schemaMapper.selectList(
            new LambdaQueryWrapper<ApiSchema>()
                .orderByDesc(ApiSchema::getCreatedAt)
        );
    }

    public ApiSchema getById(Long id) {
        return schemaMapper.selectById(id);
    }

    public ApiSchema create(ApiSchema schema) {
        schema.setCreatedAt(LocalDateTime.now());
        schema.setUpdatedAt(LocalDateTime.now());
        schemaMapper.insert(schema);
        return schema;
    }

    public ApiSchema update(ApiSchema schema) {
        ApiSchema old = schemaMapper.selectById(schema.getId());
        schema.setUpdatedAt(LocalDateTime.now());
        schemaMapper.updateById(schema);

        if (old != null && old.getSchemaJson() != null && !old.getSchemaJson().equals(schema.getSchemaJson())) {
            List<SchemaChange> changes = compareSchemaJson(old.getSchemaJson(), schema.getSchemaJson());
            if (!changes.isEmpty()) {
                boolean hasBreaking = changes.stream().anyMatch(c -> "BREAKING".equals(c.getChangeType()));
                String changeType = hasBreaking ? "BREAKING" : "MODIFIED";
                String desc = changes.stream().map(SchemaChange::getDescription).reduce((a, b) -> a + "; " + b).orElse("");

                ApiSchemaHistory history = new ApiSchemaHistory();
                history.setSchemaId(schema.getId());
                history.setChangeType(changeType);
                history.setChangeDescription(desc);
                history.setOldSchema(old.getSchemaJson());
                history.setNewSchema(schema.getSchemaJson());
                history.setCreatedAt(LocalDateTime.now());
                historyMapper.insert(history);
            }
        }

        return schema;
    }

    public void delete(Long id) {
        schemaMapper.deleteById(id);
        historyMapper.delete(
            new LambdaQueryWrapper<ApiSchemaHistory>()
                .eq(ApiSchemaHistory::getSchemaId, id)
        );
    }

    public List<ApiSchemaHistory> getHistory(Long schemaId) {
        return historyMapper.selectList(
            new LambdaQueryWrapper<ApiSchemaHistory>()
                .eq(ApiSchemaHistory::getSchemaId, schemaId)
                .orderByDesc(ApiSchemaHistory::getCreatedAt)
        );
    }

    /**
     * Compare two schema JSON strings and return structured differences.
     */
    public List<SchemaChange> compareSchemaJson(String oldSchemaJson, String newSchemaJson) {
        List<SchemaChange> changes = new ArrayList<>();
        try {
            JsonNode oldNode = objectMapper.readTree(oldSchemaJson);
            JsonNode newNode = objectMapper.readTree(newSchemaJson);
            compareRecursive(oldNode, newNode, "", changes);
        } catch (Exception e) {
            log.error("Schema comparison failed", e);
        }
        return changes;
    }

    private void compareRecursive(JsonNode oldNode, JsonNode newNode, String path, List<SchemaChange> changes) {
        if (oldNode == null && newNode == null) return;
        if (oldNode == null) {
            changes.add(new SchemaChange("ADDED", path.isEmpty() ? "/" : path, "新增节点: " + path));
            return;
        }
        if (newNode == null) {
            changes.add(new SchemaChange("REMOVED", path.isEmpty() ? "/" : path, "删除节点: " + path));
            return;
        }

        if (oldNode.isObject() && newNode.isObject()) {
            Set<String> oldFields = new LinkedHashSet<>();
            oldNode.fieldNames().forEachRemaining(oldFields::add);
            Set<String> newFields = new LinkedHashSet<>();
            newNode.fieldNames().forEachRemaining(newFields::add);

            for (String f : oldFields) {
                String fp = path.isEmpty() ? f : path + "." + f;
                if (!newFields.contains(f)) {
                    changes.add(new SchemaChange("REMOVED", fp, "删除字段 '" + fp + "'"));
                } else {
                    compareRecursive(oldNode.get(f), newNode.get(f), fp, changes);
                }
            }
            for (String f : newFields) {
                if (!oldFields.contains(f)) {
                    String fp = path.isEmpty() ? f : path + "." + f;
                    changes.add(new SchemaChange("ADDED", fp, "新增字段 '" + fp + "'"));
                }
            }
        } else if (oldNode.isArray() && newNode.isArray()) {
            int maxLen = Math.max(oldNode.size(), newNode.size());
            for (int i = 0; i < maxLen; i++) {
                String ep = path + "[" + i + "]";
                JsonNode o = i < oldNode.size() ? oldNode.get(i) : null;
                JsonNode n = i < newNode.size() ? newNode.get(i) : null;
                if (o == null) {
                    changes.add(new SchemaChange("ADDED", ep, "新增元素 " + ep));
                } else if (n == null) {
                    changes.add(new SchemaChange("REMOVED", ep, "删除元素 " + ep));
                } else {
                    compareRecursive(o, n, ep, changes);
                }
            }
        } else {
            String oldType = oldNode.isObject() ? "object" : oldNode.isArray() ? "array" : oldNode.getNodeType().name();
            String newType = newNode.isObject() ? "object" : newNode.isArray() ? "array" : newNode.getNodeType().name();
            String oldVal = oldNode.isObject() || oldNode.isArray() ? oldType : oldNode.asText();
            String newVal = newNode.isObject() || newNode.isArray() ? newType : newNode.asText();
            if (!oldVal.equals(newVal)) {
                changes.add(new SchemaChange("MODIFIED", path.isEmpty() ? "/" : path,
                    "值变更 '" + path + "': " + oldVal + " → " + newVal, oldVal, newVal));
            }
        }
    }

    /**
     * Compare a specific history version's oldSchema with its newSchema, or with current.
     */
    public Map<String, Object> compareVersions(Long historyId) {
        ApiSchemaHistory history = historyMapper.selectById(historyId);
        if (history == null) return Map.of("error", "History not found");

        List<SchemaChange> changes = compareSchemaJson(history.getOldSchema(), history.getNewSchema());
        return Map.of(
            "historyId", history.getId(),
            "changeType", history.getChangeType(),
            "changeDescription", history.getChangeDescription() != null ? history.getChangeDescription() : "",
            "oldSchema", history.getOldSchema() != null ? history.getOldSchema() : "",
            "newSchema", history.getNewSchema() != null ? history.getNewSchema() : "",
            "createdAt", history.getCreatedAt() != null ? com.litv.monitor.util.DateTimeUtil.format(history.getCreatedAt()) : "",
            "changes", changes
        );
    }

    public Map<String, Object> validateResponse(ApiSchema schema, String responseJson) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schema.getSchemaJson());
            JsonNode responseNode = objectMapper.readTree(responseJson);

            List<String> errors = new ArrayList<>();
            validateNode(responseNode, schemaNode, "", errors);

            boolean valid = errors.isEmpty();
            String message = valid ? "Validation passed" : String.join("; ", errors);

            return Map.of(
                "valid", valid,
                "message", message
            );
        } catch (Exception e) {
            log.error("Schema validation failed", e);
            return Map.of(
                "valid", false,
                "message", "Validation error: " + e.getMessage()
            );
        }
    }

    /**
     * Validate response JSON against an inline schema string.
     */
    public Map<String, Object> validateResponseJson(String schemaJson, String responseJson) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode responseNode = objectMapper.readTree(responseJson);

            List<String> errors = new ArrayList<>();
            validateNode(responseNode, schemaNode, "", errors);

            boolean valid = errors.isEmpty();
            String message = valid ? "Validation passed" : String.join("; ", errors);

            return Map.of(
                "valid", valid,
                "message", message
            );
        } catch (Exception e) {
            return Map.of(
                "valid", false,
                "message", "Validation error: " + e.getMessage()
            );
        }
    }

    private void validateNode(JsonNode data, JsonNode schema, String path, List<String> errors) {
        if (schema.has("type")) {
            String expectedType = schema.get("type").asText();
            String actualType = getJsonType(data);
            if (!expectedType.equals(actualType)) {
                errors.add("Type mismatch at " + path + ": expected " + expectedType + ", got " + actualType);
            }
        }

        if (schema.has("required") && schema.get("required").isArray() && data.isObject()) {
            Iterator<JsonNode> elements = schema.get("required").elements();
            while (elements.hasNext()) {
                String fieldName = elements.next().asText();
                if (!data.has(fieldName)) {
                    errors.add("Missing required field: " + path + "." + fieldName);
                }
            }
        }

        if (schema.has("properties") && data.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = schema.get("properties").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                if (data.has(fieldName)) {
                    validateNode(data.get(fieldName), entry.getValue(), path + "." + fieldName, errors);
                }
            }
        }

        if (schema.has("items") && data.isArray()) {
            for (int i = 0; i < data.size(); i++) {
                validateNode(data.get(i), schema.get("items"), path + "[" + i + "]", errors);
            }
        }
    }

    public String getJsonType(JsonNode node) {
        if (node.isNull()) return "null";
        if (node.isBoolean()) return "boolean";
        if (node.isInt()) return "integer";
        if (node.isNumber()) return "number";
        if (node.isTextual()) return "string";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        return "string";
    }

    /**
     * Detect all changes between expected schema and actual response JSON.
     * Returns a list of SchemaChange with type ADDED/REMOVED/MODIFIED.
     */
    public List<SchemaChange> detectChanges(String expectedSchemaJson, String actualResponseJson) {
        List<SchemaChange> changes = new ArrayList<>();
        try {
            JsonNode expectedSchema = objectMapper.readTree(expectedSchemaJson);
            JsonNode actualJson = objectMapper.readTree(actualResponseJson);
            List<String> schemaFields = new ArrayList<>();
            expectedSchema.fieldNames().forEachRemaining(schemaFields::add);
            List<String> dataFields = new ArrayList<>();
            actualJson.fieldNames().forEachRemaining(dataFields::add);
            log.info("detectChanges: expectedSchema fields={} actualJson fields={}", schemaFields, dataFields);
            detectChangesRecursive(expectedSchema, actualJson, "", changes);
            log.info("detectChanges result: {} changes", changes.size());
            for (SchemaChange c : changes) {
                log.info("  change: {} | {} | {}", c.getChangeType(), c.getFieldPath(), c.getDescription());
            }
        } catch (Exception e) {
            log.error("Schema change detection failed", e);
        }
        return changes;
    }

    private void detectChangesRecursive(JsonNode expectedSchema, JsonNode actualData, String path, List<SchemaChange> changes) {
        if (expectedSchema == null || actualData == null) return;

        if (expectedSchema.has("properties") && actualData.isObject()) {
            Set<String> expectedFields = new LinkedHashSet<>();
            Iterator<Map.Entry<String, JsonNode>> it = expectedSchema.get("properties").fields();
            while (it.hasNext()) {
                expectedFields.add(it.next().getKey());
            }

            Set<String> actualFields = new LinkedHashSet<>();
            Iterator<Map.Entry<String, JsonNode>> it2 = actualData.fields();
            while (it2.hasNext()) {
                actualFields.add(it2.next().getKey());
            }

            for (String field : expectedFields) {
                if (!actualFields.contains(field)) {
                    String fieldPath = path.isEmpty() ? field : path + "." + field;
                    boolean isRequired = isRequiredField(expectedSchema, field);
                    String desc = "字段 '" + fieldPath + "' 已删除";
                    if (isRequired) desc += "（必填）";
                    changes.add(new SchemaChange("REMOVED", fieldPath, desc));
                }
            }

            for (String field : actualFields) {
                if (!expectedFields.contains(field)) {
                    String fieldPath = path.isEmpty() ? field : path + "." + field;
                    changes.add(new SchemaChange("ADDED", fieldPath, "新增字段 '" + fieldPath + "'"));
                }
            }

            for (String field : expectedFields) {
                if (actualFields.contains(field)) {
                    JsonNode expectedProp = expectedSchema.get("properties").get(field);
                    JsonNode actualValue = actualData.get(field);
                    String fieldPath = path.isEmpty() ? field : path + "." + field;

                    if (expectedProp.has("type")) {
                        String expectedType = expectedProp.get("type").asText();
                        String actualType = getJsonType(actualValue);
                        if (!expectedType.equals(actualType)) {
                            changes.add(new SchemaChange("MODIFIED", fieldPath,
                                "字段 '" + fieldPath + "' 类型变更: " + expectedType + " → " + actualType,
                                expectedType, actualType));
                        }
                    }

                    if (expectedProp.has("properties") && actualValue.isObject()) {
                        detectChangesRecursive(expectedProp, actualValue, fieldPath, changes);
                    }
                }
            }
        } else if (actualData.isObject()) {
            // No properties defined, but check required fields
            if (expectedSchema.has("required") && expectedSchema.get("required").isArray()) {
                Iterator<JsonNode> reqIt = expectedSchema.get("required").elements();
                while (reqIt.hasNext()) {
                    String reqField = reqIt.next().asText();
                    if (!actualData.has(reqField)) {
                        String fieldPath = path.isEmpty() ? reqField : path + "." + reqField;
                        changes.add(new SchemaChange("REMOVED", fieldPath,
                            "必填字段 '" + fieldPath + "' 缺失（required 声明）"));
                    }
                }
            }
        }
    }

    private boolean isRequiredField(JsonNode schema, String fieldName) {
        if (schema.has("required") && schema.get("required").isArray()) {
            Iterator<JsonNode> it = schema.get("required").elements();
            while (it.hasNext()) {
                if (it.next().asText().equals(fieldName)) return true;
            }
        }
        return false;
    }

    /**
     * Check schema changes and record history (used by the old scheduled check).
     */
    public void checkSchemaChanges(ApiSchema schema, String newResponseJson) {
        try {
            JsonNode newSchemaNode = generateSchemaFromResponse(newResponseJson);
            String newSchemaJson = objectMapper.writeValueAsString(newSchemaNode);

            if (schema.getSchemaJson() == null) {
                schema.setSchemaJson(newSchemaJson);
                schemaMapper.updateById(schema);
                return;
            }

            List<SchemaChange> changes = detectChanges(schema.getSchemaJson(), newSchemaJson);

            if (!changes.isEmpty()) {
                boolean hasBreaking = changes.stream().anyMatch(c -> "REMOVED".equals(c.getChangeType()));
                String changeType = hasBreaking ? "BREAKING" : "MODIFIED";
                String desc = changes.stream().map(SchemaChange::getDescription).reduce((a, b) -> a + "; " + b).orElse("");

                ApiSchemaHistory history = new ApiSchemaHistory();
                history.setSchemaId(schema.getId());
                history.setChangeType(changeType);
                history.setChangeDescription(desc);
                history.setOldSchema(schema.getSchemaJson());
                history.setNewSchema(newSchemaJson);
                history.setCreatedAt(LocalDateTime.now());
                historyMapper.insert(history);

                schema.setLastCheckStatus(changeType);
                schema.setLastCheckMessage(desc);
            } else {
                schema.setLastCheckStatus("OK");
                schema.setLastCheckMessage("No changes detected");
            }

            schema.setLastCheckedAt(LocalDateTime.now());
            schemaMapper.updateById(schema);
        } catch (Exception e) {
            log.error("Schema change detection failed", e);
            schema.setLastCheckStatus("ERROR");
            schema.setLastCheckMessage("Change detection failed: " + e.getMessage());
            schema.setLastCheckedAt(LocalDateTime.now());
            schemaMapper.updateById(schema);
        }
    }

    private JsonNode generateSchemaFromResponse(String responseJson) throws Exception {
        JsonNode responseNode = objectMapper.readTree(responseJson);
        return generateJsonSchema(responseNode);
    }

    private JsonNode generateJsonSchema(JsonNode node) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", getJsonType(node));

        if (node.isObject()) {
            ObjectNode properties = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                properties.set(entry.getKey(), generateJsonSchema(entry.getValue()));
            }
            schema.set("properties", properties);
        } else if (node.isArray() && node.size() > 0) {
            schema.set("items", generateJsonSchema(node.get(0)));
        }

        return schema;
    }

    public List<Map<String, Object>> listAllHistory(Integer hours, String changeType) {
        StringBuilder sql = new StringBuilder(
            "SELECT h.id, h.schema_id, h.change_type, h.change_description, h.old_schema, h.new_schema, h.created_at, " +
            "s.name as schema_name FROM api_schema_history h " +
            "LEFT JOIN api_schema s ON h.schema_id = s.id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (hours != null && hours > 0) {
            sql.append("AND REPLACE(h.created_at, 'T', ' ') >= datetime(?, 'localtime', '-' || ? || ' hours') ");
            params.add(com.litv.monitor.util.DateTimeUtil.now());
            params.add(hours);
        }
        if (changeType != null && !changeType.isEmpty()) {
            sql.append("AND h.change_type = ? ");
            params.add(changeType);
        }
        sql.append("ORDER BY h.created_at DESC");

        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.warn("Failed to query all schema history: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
