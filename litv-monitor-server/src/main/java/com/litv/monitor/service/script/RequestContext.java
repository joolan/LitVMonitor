package com.litv.monitor.service.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Data
public class RequestContext {

    private String method;
    private String url;
    private String baseUrl;
    private Map<String, String> headers;
    private Map<String, String> params;
    private String body;
    private String bodyType;
    private long timestamp;
    private String timestampS;
    private String datetime;
    private String time;
    private String date;
    private String nonce;
    private String uuid;

    public RequestContext(String method, String url, String headers, String params, String body) {
        this.method = method;
        this.url = url;
        this.headers = parseJsonToMap(headers);
        this.params = parseQueryString(params);
        this.body = body;
        this.bodyType = "application/json";

        ZoneId beijing = ZoneId.of("Asia/Shanghai");
        LocalDateTime now = LocalDateTime.now(beijing);
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        this.timestamp = System.currentTimeMillis();
        this.timestampS = String.valueOf(this.timestamp / 1000);
        this.datetime = now.format(dtFmt);
        this.time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        this.date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        this.nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        this.uuid = UUID.randomUUID().toString();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    private Map<String, String> parseJsonToMap(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) return result;
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(json);
            node.fields().forEachRemaining(entry ->
                result.put(entry.getKey(), entry.getValue().asText())
            );
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    private Map<String, String> parseQueryString(String qs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (qs == null || qs.isEmpty()) return result;
        for (String pair : qs.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else if (!pair.isEmpty()) {
                result.put(pair, "");
            }
        }
        return result;
    }

    public ScriptResult toResult() {
        ScriptResult result = new ScriptResult();
        result.setAddHeaders(new LinkedHashMap<>(this.headers));
        result.setAddParams(new LinkedHashMap<>(this.params));
        result.setRawBody(this.body);
        return result;
    }
}
