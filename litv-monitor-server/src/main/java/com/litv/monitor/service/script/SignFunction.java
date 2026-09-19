package com.litv.monitor.service.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public interface SignFunction {

    String sign(RequestContext ctx, JsonNode config);

    default ObjectMapper getMapper() {
        return new ObjectMapper();
    }
}
