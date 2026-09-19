package com.litv.monitor.service.script;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class Md5SignFunction implements SignFunction {

    @Override
    public String sign(RequestContext ctx, JsonNode config) {
        try {
            String secretKey = config.has("secretKey") ? config.get("secretKey").asText() : "";
            String excludeKeys = config.has("excludeKeys") ? config.get("excludeKeys").asText() : "";
            String signKey = config.has("signFieldName") ? config.get("signFieldName").asText() : "sign";
            String timestampParam = config.has("timestampParam") ? config.get("timestampParam").asText() : "timestamp";
            String includeTimestamp = config.has("includeTimestamp") ? config.get("includeTimestamp").asText() : "true";

            // Collect all parameters from params + body
            TreeMap<String, String> allParams = new TreeMap<>();

            // Add URL params
            if (ctx.getParams() != null) {
                allParams.putAll(ctx.getParams());
            }

            // Add body params if JSON body
            if (ctx.getBody() != null && !ctx.getBody().isEmpty()) {
                try {
                    JsonNode bodyNode = getMapper().readTree(ctx.getBody());
                    if (bodyNode.isObject()) {
                        bodyNode.fields().forEachRemaining(entry ->
                            allParams.put(entry.getKey(), entry.getValue().asText())
                        );
                    }
                } catch (Exception e) {
                    // Not JSON body, skip
                }
            }

            // Add timestamp if needed
            if ("true".equalsIgnoreCase(includeTimestamp) && !allParams.containsKey(timestampParam)) {
                allParams.put(timestampParam, ctx.getTimestampS());
            }

            // Exclude keys
            String[] excludes = excludeKeys.split(",");
            for (String ex : excludes) {
                String trimmed = ex.trim();
                if (!trimmed.isEmpty()) {
                    allParams.remove(trimmed);
                }
            }

            // Remove existing sign field
            allParams.remove(signKey);

            // Build canonical string
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
                if (sb.length() > 0) sb.append("&");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }

            // Append secret key
            if (!secretKey.isEmpty()) {
                sb.append("&key=").append(secretKey);
            }

            String canonical = sb.toString();
            log.debug("MD5 sign canonical: {}", canonical);

            // MD5 hash
            return DigestUtils.md5DigestAsHex(canonical.getBytes(StandardCharsets.UTF_8)).toUpperCase();

        } catch (Exception e) {
            log.error("MD5 sign error", e);
            return "";
        }
    }
}
