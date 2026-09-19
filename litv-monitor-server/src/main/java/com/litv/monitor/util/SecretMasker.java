package com.litv.monitor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * 告警渠道/配置中敏感字段的脱敏工具。
 * 响应时把密钥替换为哨兵值 {@link #SENTINEL}；更新时若前端回传哨兵值，则用库中旧值回填。
 */
public final class SecretMasker {

    public static final String SENTINEL = "******";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 判断字段名是否为敏感字段（宽松匹配，覆盖 smtpPassword / accessToken 等）。 */
    private static boolean isSecretKey(String lower) {
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.contains("privatekey")
                || lower.contains("private_key")
                || lower.equals("key")
                || lower.equals("sign");
    }

    private SecretMasker() {
    }

    public static String mask(String json) {
        if (json == null || json.isEmpty()) return json;
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isObject()) return json;
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                JsonNode val = e.getValue();
                String lower = key.toLowerCase();
                if (isSecretKey(lower)) {
                    if (val.isTextual() && !val.asText().isEmpty()) {
                        obj.put(key, SENTINEL);
                    }
                } else if ("url".equals(lower) && val.isTextual()) {
                    obj.put(key, maskUrl(val.asText()));
                }
            }
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    /** 掩码 URL 中 token/key/secret/sign 查询参数的值。 */
    public static String maskUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            int q = url.indexOf('?');
            if (q < 0) return url;
            String base = url.substring(0, q);
            String query = url.substring(q + 1);
            StringBuilder sb = new StringBuilder(base).append('?');
            String[] parts = query.split("&");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append('&');
                int eq = parts[i].indexOf('=');
                if (eq < 0) {
                    sb.append(parts[i]);
                    continue;
                }
                String k = parts[i].substring(0, eq);
                String v = parts[i].substring(eq + 1);
                String lk = k.toLowerCase();
                if ((lk.contains("token") || lk.contains("key") || lk.contains("secret") || lk.contains("sign")) && !v.isEmpty()) {
                    sb.append(k).append('=').append(SENTINEL);
                } else {
                    sb.append(k).append('=').append(v);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    /** 用旧配置中的真实值回填新配置中的哨兵值（表示用户未修改该密钥）。 */
    public static String restore(String newJson, String oldJson) {
        if (newJson == null || newJson.isEmpty()) return oldJson;
        if (oldJson == null || oldJson.isEmpty()) return newJson;
        try {
            JsonNode newNode = MAPPER.readTree(newJson);
            JsonNode oldNode = MAPPER.readTree(oldJson);
            if (!newNode.isObject() || !oldNode.isObject()) return newJson;
            ObjectNode obj = (ObjectNode) newNode;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                JsonNode val = e.getValue();
                String lower = key.toLowerCase();
                if (isSecretKey(lower) && val.isTextual() && SENTINEL.equals(val.asText())
                        && oldNode.has(key) && !oldNode.get(key).isNull()) {
                    obj.set(key, oldNode.get(key));
                } else if ("url".equals(lower) && val.isTextual() && oldNode.has(key)
                        && val.asText().equals(maskUrl(oldNode.get(key).asText()))) {
                    obj.set(key, oldNode.get(key));
                }
            }
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return newJson;
        }
    }
}
