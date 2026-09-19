package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litv.monitor.entity.GlobalVariable;
import com.litv.monitor.entity.GroupVariable;
import com.litv.monitor.mapper.GlobalVariableMapper;
import com.litv.monitor.mapper.GroupVariableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VariableEngine {

    private final GlobalVariableMapper globalVariableMapper;
    private final GroupVariableMapper groupVariableMapper;
    private final ObjectMapper objectMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w[\\w\\-]*\\.\\w[\\w\\-]*)}}");

    public String replaceVariables(String template, Long groupId) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        return replaceVariables(template, loadVariables(groupId));
    }

    /**
     * Load global + group variables once, to be reused across multiple replacements
     * within a single monitor execution (avoids repeated full-table queries).
     */
    public Map<String, String> loadVariables(Long groupId) {
        Map<String, String> variables = new HashMap<>();
        loadGlobalVariables(variables);
        if (groupId != null) {
            loadGroupVariables(groupId, variables);
        }
        return variables;
    }

    public String replaceVariables(String template, Map<String, String> baseVars) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Map<String, String> variables = new HashMap<>(baseVars);
        // Add built-in variables (instant - regenerated each call)
        variables.putAll(getInstantVariables());

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public Map<String, String> replaceVariablesWithTrace(String template, Long groupId) {
        if (template == null || template.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return replaceVariablesWithTrace(template, loadVariables(groupId));
    }

    public Map<String, String> replaceVariablesWithTrace(String template, Map<String, String> baseVars) {
        Map<String, String> result = new LinkedHashMap<>();
        if (template == null || template.isEmpty()) {
            return result;
        }

        Map<String, String> variables = new HashMap<>(baseVars);
        variables.putAll(getInstantVariables());

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "");
            result.put(varName, value);
        }
        return result;
    }

    public Map<String, String[]> extractVariablesWithTrace(String configJson, String responseBody,
                                                            Map<String, String> responseHeaders,
                                                            Map<String, String> responseCookies,
                                                            Long groupId) {
        Map<String, String[]> trace = new LinkedHashMap<>();
        if (configJson == null || configJson.isEmpty()) {
            return trace;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(configJson);
            config.fields().forEachRemaining(entry -> {
                String varName = entry.getKey();
                com.fasterxml.jackson.databind.JsonNode varConfig = entry.getValue();
                String path = varConfig.get("jsonPath").asText();
                String defaultValue = varConfig.has("defaultValue") ? varConfig.get("defaultValue").asText() : null;
                String source = varConfig.has("source") ? varConfig.get("source").asText() : "body";

                try {
                    String extractedValue = null;
                    boolean isFullValue = (path == null || path.isEmpty());

                    if ("header".equals(source)) {
                        if (isFullValue) {
                            extractedValue = responseHeaders != null ? objectMapper.writeValueAsString(responseHeaders) : null;
                        } else {
                            extractedValue = responseHeaders != null ? responseHeaders.get(path.toLowerCase()) : null;
                        }
                    } else if ("cookie".equals(source)) {
                        if (isFullValue) {
                            extractedValue = responseCookies != null ? objectMapper.writeValueAsString(responseCookies) : null;
                        } else {
                            extractedValue = responseCookies != null ? responseCookies.get(path) : null;
                        }
                    } else {
                        if (isFullValue) {
                            extractedValue = responseBody;
                        } else {
                            Object value = com.jayway.jsonpath.JsonPath.read(responseBody, path);
                            extractedValue = value != null ? value.toString() : null;
                        }
                    }

                    if (extractedValue == null) extractedValue = defaultValue;
                    if (extractedValue == null) return;

                    if (varName.startsWith("global.")) {
                        String realName = varName.substring("global.".length());
                        setGlobalVariable(realName, extractedValue);
                    } else if (varName.startsWith("group.") && groupId != null) {
                        String realName = varName.substring("group.".length());
                        setGroupVariable(groupId, realName, extractedValue);
                    } else if (groupId != null) {
                        setGroupVariable(groupId, varName, extractedValue);
                    } else {
                        setGlobalVariable(varName, extractedValue);
                    }
                    trace.put(varName, new String[]{source + ":" + (isFullValue ? "(全量)" : path), extractedValue});
                } catch (Exception e) {
                    if (defaultValue != null) {
                        if (varName.startsWith("global.")) {
                            setGlobalVariable(varName.substring("global.".length()), defaultValue);
                        } else if (varName.startsWith("group.") && groupId != null) {
                            setGroupVariable(groupId, varName.substring("group.".length()), defaultValue);
                        } else if (groupId != null) {
                            setGroupVariable(groupId, varName, defaultValue);
                        } else {
                            setGlobalVariable(varName, defaultValue);
                        }
                        trace.put(varName, new String[]{source + ":" + path + " (fallback)", defaultValue});
                    }
                }
            });
        } catch (Exception e) {
            trace.put("_error", new String[]{e.getMessage()});
        }
        return trace;
    }

    public Map<String, String[]> extractVariablesWithTrace(String configJson, String responseBody, Long groupId) {
        return extractVariablesWithTrace(configJson, responseBody, null, null, groupId);
    }

    private Map<String, String> getInstantVariables() {
        Map<String, String> vars = new HashMap<>();
        ZoneId beijing = ZoneId.of("Asia/Shanghai");
        LocalDateTime now = LocalDateTime.now(beijing);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dtMsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        long ms = System.currentTimeMillis();

        vars.put("env.timestamp", String.valueOf(ms));
        vars.put("env.timestamp_s", String.valueOf(ms / 1000));
        vars.put("env.time", now.format(timeFmt));
        vars.put("env.date", now.format(dateFmt));
        vars.put("env.datetime", now.format(dtFmt));
        vars.put("env.datetime_ms", now.format(dtMsFmt));
        vars.put("env.unix", String.valueOf(ms / 1000));
        vars.put("env.uuid", java.util.UUID.randomUUID().toString());
        vars.put("env.uuid_short", java.util.UUID.randomUUID().toString().substring(0, 8));
        vars.put("env.random", String.valueOf(new Random().nextInt(1000000)));
        vars.put("env.nonce", generateNonce(32));

        return vars;
    }

    private String generateNonce(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void loadGlobalVariables(Map<String, String> variables) {
        List<GlobalVariable> globalVars = globalVariableMapper.selectList(null);
        for (GlobalVariable var : globalVars) {
            variables.put("global." + var.getName(), var.getValue());
        }
    }

    private void loadGroupVariables(Long groupId, Map<String, String> variables) {
        LambdaQueryWrapper<GroupVariable> wrapper = new LambdaQueryWrapper<GroupVariable>();
        if (groupId != null) {
            wrapper.eq(GroupVariable::getGroupId, groupId);
        }
        List<GroupVariable> groupVars = groupVariableMapper.selectList(wrapper);
        for (GroupVariable var : groupVars) {
            if (var.getValue() != null && !var.getValue().isEmpty()) {
                variables.put("group." + var.getName(), var.getValue());
            }
        }
    }

    public void setGroupVariable(Long groupId, String name, String value) {
        LambdaQueryWrapper<GroupVariable> wrapper = new LambdaQueryWrapper<GroupVariable>()
                .eq(GroupVariable::getGroupId, groupId)
                .eq(GroupVariable::getName, name);
        GroupVariable existing = groupVariableMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setValue(value);
            groupVariableMapper.updateById(existing);
        } else {
            GroupVariable newVar = new GroupVariable();
            newVar.setGroupId(groupId);
            newVar.setName(name);
            newVar.setValue(value);
            newVar.setScope("GROUP");
            groupVariableMapper.insert(newVar);
        }
    }

    public void setGlobalVariable(String name, String value) {
        LambdaQueryWrapper<GlobalVariable> wrapper = new LambdaQueryWrapper<GlobalVariable>()
                .eq(GlobalVariable::getName, name);
        GlobalVariable existing = globalVariableMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setValue(value);
            globalVariableMapper.updateById(existing);
        } else {
            GlobalVariable newVar = new GlobalVariable();
            newVar.setName(name);
            newVar.setValue(value);
            globalVariableMapper.insert(newVar);
        }
    }

    public void clearGroupVariables(Long groupId) {
        LambdaQueryWrapper<GroupVariable> wrapper = new LambdaQueryWrapper<GroupVariable>()
                .eq(GroupVariable::getGroupId, groupId);
        groupVariableMapper.delete(wrapper);
    }
}
