package com.litv.monitor.service.script;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ScriptResult {

    private Map<String, String> addHeaders = new LinkedHashMap<>();

    private Map<String, String> addParams = new LinkedHashMap<>();

    private Map<String, String> replaceBodyFields = new LinkedHashMap<>();

    private String rawBody;

    private String error;

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
}
