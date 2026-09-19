package com.litv.monitor.service.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PreRequestScriptEngine {

    private final Map<String, SignFunction> builtinSigns = Map.of(
            "MD5_SIGN", new Md5SignFunction(),
            "HMAC_SHA256", new HmacSha256SignFunction(),
            "RSA_SHA256", new RsaSha256SignFunction()
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** 共享 Engine，避免每次执行都重建（Context 仍按执行创建，保证线程安全）。 */
    private static final Engine SHARED_ENGINE = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    /**
     * JS prelude exposing the documented {@code crypto} / {@code util} helpers.
     * All heavy lifting is delegated to the sandboxed host object {@code __rt}
     * ({@link ScriptRuntime}); arbitrary Java access is intentionally unavailable.
     */
    private static final String SCRIPT_PRELUDE = """
            const crypto = {
              md5: function(str) { return __h('md5', str); },
              md5Hex: function(str) { return __h('md5Hex', str); },
              sha1: function(str) { return __h('sha1', str); },
              sha256: function(str) { return __h('sha256', str); },
              hmacSha256: function(str, key) { return __h('hmacSha256', str, key); },
              hmacSha256Base64: function(str, key) { return __h('hmacSha256Base64', str, key); },
              hmacMd5: function(str, key) { return __h('hmacMd5', str, key); },
              base64: function(str) { return __h('base64', str); },
              base64Decode: function(str) { return __h('base64Decode', str); },
              rsaSha256Sign: function(str, pem) { return __h('rsaSha256Sign', str, pem); },
              rsaSha256SignHex: function(str, pem) { return __h('rsaSha256SignHex', str, pem); }
            };
            const util = {
              timestamp: function() { return __h('timestamp'); },
              timestampS: function() { return __h('timestampS'); },
              datetime: function() { return __h('datetime'); },
              time: function() { return __h('time'); },
              date: function() { return __h('date'); },
              nonce: function(len) { return __h('nonce', len); },
              uuid: function() { return __h('uuid'); },
              uuidShort: function() { return __h('uuidShort'); },
              random: function(max) { return __h('random', max); },
              parseInt: function(s) { return parseInt(s); },
              parseFloat: function(s) { return parseFloat(s); }
            };
            """;

    public ScriptResult execute(Monitor monitor, RequestContext requestContext) {
        boolean hasBuiltinSign = monitor.getSignType() != null && !"NONE".equals(monitor.getSignType());
        boolean hasCustomScript = monitor.getPreRequestScript() != null && !monitor.getPreRequestScript().isEmpty();

        if (!hasBuiltinSign && !hasCustomScript) {
            return requestContext.toResult();
        }

        // A custom pre-request script is authoritative (the UI stores only the script),
        // so the built-in sign must not run as well to avoid double-signing / polluted
        // canonical strings. The built-in sign is only a fallback for legacy monitors
        // that have no script.
        if (hasCustomScript) {
            executeCustomScript(monitor.getPreRequestScript(), requestContext);
        } else {
            executeBuiltinSign(monitor, requestContext);
        }

        return requestContext.toResult();
    }

    private void executeBuiltinSign(Monitor monitor, RequestContext ctx) {
        SignFunction signFn = builtinSigns.get(monitor.getSignType());
        if (signFn == null) {
            log.warn("Unknown sign type: {}", monitor.getSignType());
            return;
        }

        JsonNode config = null;
        if (monitor.getSignConfig() != null && !monitor.getSignConfig().isEmpty()) {
            try {
                config = MAPPER.readTree(monitor.getSignConfig());
            } catch (Exception e) {
                log.error("Failed to parse sign config: {}", e.getMessage());
            }
        }
        if (config == null) {
            config = MAPPER.createObjectNode();
        }

        String signature = signFn.sign(ctx, config);
        if (signature.isEmpty()) {
            log.warn("Sign function returned empty result for type: {}", monitor.getSignType());
            return;
        }

        String fieldName = monitor.getSignFieldName();
        if (fieldName == null || fieldName.isEmpty()) {
            fieldName = "sign";
        }

        String target = monitor.getSignTarget();
        if (target == null || target.isEmpty()) {
            target = "HEADER";
        }

        switch (target.toUpperCase()) {
            case "HEADER":
                ctx.getHeaders().put(fieldName, signature);
                break;
            case "PARAMS":
                ctx.getParams().put(fieldName, signature);
                break;
            case "BODY":
                injectIntoBody(ctx, fieldName, signature);
                break;
            case "MULTIPLE":
                ctx.getHeaders().put(fieldName, signature);
                ctx.getParams().put(fieldName, signature);
                injectIntoBody(ctx, fieldName, signature);
                break;
        }

        log.debug("Applied builtin sign [{}] -> {}: {}", monitor.getSignType(), target, fieldName);
    }

    private void injectIntoBody(RequestContext ctx, String fieldName, String value) {
        if (ctx.getBody() == null || ctx.getBody().isEmpty()) {
            Map<String, String> bodyMap = new LinkedHashMap<>();
            bodyMap.put(fieldName, value);
            try {
                ctx.setBody(MAPPER.writeValueAsString(bodyMap));
            } catch (Exception e) {
                ctx.setBody("{\"" + fieldName + "\":\"" + value + "\"}");
            }
            return;
        }
        try {
            JsonNode bodyNode = MAPPER.readTree(ctx.getBody());
            if (bodyNode.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode objNode = (com.fasterxml.jackson.databind.node.ObjectNode) bodyNode;
                objNode.put(fieldName, value);
                ctx.setBody(MAPPER.writeValueAsString(objNode));
            }
        } catch (Exception e) {
            log.warn("Failed to inject sign into body: {}", e.getMessage());
        }
    }

    private void executeCustomScript(String script, RequestContext ctx) {
        try (Context context = Context.newBuilder("js")
                .engine(SHARED_ENGINE)
                .allowHostAccess(HostAccess.UNTRUSTED)
                .allowExperimentalOptions(true)
                .option("js.ecmascript-version", "2023")
                .option("js.strict", "true")
                .build()) {

            // Sandboxed host helpers (crypto / util) exposed as a guest-callable
            // ProxyExecutable so no arbitrary Java access is granted to the script.
            ScriptRuntime runtime = new ScriptRuntime();
            context.getBindings("js").putMember("__h", (ProxyExecutable) args -> invokeRuntime(runtime, args));

            StringBuilder env = new StringBuilder();
            env.append(SCRIPT_PRELUDE);

            // request object
            env.append("var request = {\n");
            env.append("  method: '").append(escapeJs(ctx.getMethod())).append("',\n");
            env.append("  url: '").append(escapeJs(ctx.getUrl())).append("',\n");
            env.append("  body: '").append(escapeJs(ctx.getBody() != null ? ctx.getBody() : "")).append("',\n");
            env.append("  bodyType: '").append(escapeJs(ctx.getBodyType())).append("',\n");
            env.append("  headers: ").append(mapToJson(ctx.getHeaders())).append(",\n");
            env.append("  params: ").append(mapToJson(ctx.getParams())).append("\n");
            env.append("};\n\n");

            // Utility function to modify request
            env.append("function setHeader(key, value) { request.headers[key] = value; }\n");
            env.append("function setParam(key, value) { request.params[key] = value; }\n");
            env.append("function setBody(body) { request.body = body; }\n");
            env.append("function setBodyField(key, value) {\n");
            env.append("  try {\n");
            env.append("    var obj = JSON.parse(request.body);\n");
            env.append("    obj[key] = value;\n");
            env.append("    request.body = JSON.stringify(obj);\n");
            env.append("  } catch(e) {\n");
            env.append("    request.body = JSON.stringify({[key]: value});\n");
            env.append("  }\n");
            env.append("}\n\n");

            // User script
            env.append(script);

            context.eval("js", env.toString());

            // Extract modified request back
            Value requestValue = context.getBindings("js").getMember("request");
            if (requestValue != null) {
                ctx.setBody(valueToString(requestValue.getMember("body")));
                ctx.setMethod(valueToString(requestValue.getMember("method")));
                ctx.setUrl(valueToString(requestValue.getMember("url")));

                // Extract headers
                Value headers = requestValue.getMember("headers");
                if (headers != null && headers.hasMembers()) {
                    ctx.getHeaders().clear();
                    for (String key : headers.getMemberKeys()) {
                        ctx.getHeaders().put(key, valueToString(headers.getMember(key)));
                    }
                }

                // Extract params
                Value params = requestValue.getMember("params");
                if (params != null && params.hasMembers()) {
                    ctx.getParams().clear();
                    for (String key : params.getMemberKeys()) {
                        ctx.getParams().put(key, valueToString(params.getMember(key)));
                    }
                }
            }

            log.debug("Custom pre-request script executed successfully");

        } catch (Exception e) {
            log.error("Failed to execute pre-request script: {}", e.getMessage(), e);
        }
    }

    private Object invokeRuntime(ScriptRuntime rt, Value[] args) {
        String fn = argStr(args, 0);
        if (fn == null) return "";
        switch (fn) {
            case "md5": return rt.md5(argStr(args, 1));
            case "md5Hex": return rt.md5Hex(argStr(args, 1));
            case "sha1": return rt.sha1(argStr(args, 1));
            case "sha256": return rt.sha256(argStr(args, 1));
            case "hmacSha256": return rt.hmacSha256(argStr(args, 1), argStr(args, 2));
            case "hmacSha256Base64": return rt.hmacSha256Base64(argStr(args, 1), argStr(args, 2));
            case "hmacMd5": return rt.hmacMd5(argStr(args, 1), argStr(args, 2));
            case "base64": return rt.base64(argStr(args, 1));
            case "base64Decode": return rt.base64Decode(argStr(args, 1));
            case "rsaSha256Sign": return rt.rsaSha256Sign(argStr(args, 1), argStr(args, 2));
            case "rsaSha256SignHex": return rt.rsaSha256SignHex(argStr(args, 1), argStr(args, 2));
            case "timestamp": return rt.timestamp();
            case "timestampS": return rt.timestampS();
            case "datetime": return rt.datetime();
            case "time": return rt.time();
            case "date": return rt.date();
            case "nonce": return rt.nonce(argInt(args, 1));
            case "uuid": return rt.uuid();
            case "uuidShort": return rt.uuidShort();
            case "random": return rt.random(argInt(args, 1));
            default: throw new RuntimeException("未知脚本函数: " + fn);
        }
    }

    private String argStr(Value[] args, int i) {
        if (args.length <= i || args[i] == null || args[i].isNull()) return null;
        Value v = args[i];
        if (v.isString()) return v.asString();
        if (v.isNumber()) {
            double d = v.asDouble();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return v.toString();
    }

    private Integer argInt(Value[] args, int i) {
        if (args.length <= i || args[i] == null || args[i].isNull() || !args[i].isNumber()) return null;
        return (int) args[i].asLong();
    }

    private String valueToString(Value v) {
        if (v == null || v.isNull()) return "";
        if (v.isString()) return v.asString();
        if (v.isNumber()) {
            double d = v.asDouble();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (v.isBoolean()) return String.valueOf(v.asBoolean());
        return v.toString();
    }

    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("'").append(escapeJs(entry.getKey())).append("':'").append(escapeJs(entry.getValue())).append("'");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
