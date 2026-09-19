package com.litv.monitor.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.entity.ProxyConfig;
import com.litv.monitor.entity.AlertTemplate;
import com.litv.monitor.mapper.AlertTemplateMapper;
import com.litv.monitor.service.*;
import com.litv.monitor.service.script.PreRequestScriptEngine;
import com.litv.monitor.service.script.RequestContext;
import com.litv.monitor.service.script.ScriptResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpMonitorExecutor implements MonitorExecutor {

    private final VariableEngine variableEngine;
    private final DomainAssetService domainAssetService;
    private final SslService sslService;
    private final ApiSchemaService apiSchemaService;
    private final PreRequestScriptEngine preRequestScriptEngine;
    private final ProxyConfigService proxyConfigService;
    private final AlertTemplateMapper alertTemplateMapper;
    private final SsrfProtectionService ssrfGuard;
    private final ObjectMapper objectMapper;

    private volatile OkHttpClient sharedClient;
    private volatile ProxyConfig lastUsedProxyConfig;

    @org.springframework.beans.factory.annotation.Value("${monitor.ssl-verify-disabled:false}")
    private boolean sslVerifyDisabled;

    @org.springframework.beans.factory.annotation.Value("${monitor.log-body-max-size:262144}")
    private int logBodyMaxSize;

    @org.springframework.beans.factory.annotation.Value("${monitor.domain-asset-update-interval-minutes:10}")
    private int domainRecordIntervalMinutes;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> domainRecordTimes = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public String getMonitorType() {
        return "HTTP";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> baseVars) {
        long startTime = System.currentTimeMillis();
        int timeout = monitor.getTimeout() != null ? monitor.getTimeout() : 30;

        try {
            Map<String, String> urlRefs = variableEngine.replaceVariablesWithTrace(monitor.getUrl(), baseVars);
            Map<String, String> headerRefs = variableEngine.replaceVariablesWithTrace(monitor.getHeaders(), baseVars);
            Map<String, String> bodyRefs = variableEngine.replaceVariablesWithTrace(monitor.getBody(), baseVars);
            String url = variableEngine.replaceVariables(monitor.getUrl(), baseVars);
            String headers = variableEngine.replaceVariables(monitor.getHeaders(), baseVars);
            String body = variableEngine.replaceVariables(monitor.getBody(), baseVars);
            logEntry.setUrl(url);

            Map<String, String> allRefs = new LinkedHashMap<>();
            allRefs.putAll(urlRefs);
            allRefs.putAll(headerRefs);
            allRefs.putAll(bodyRefs);
            if (!allRefs.isEmpty()) {
                logEntry.setVariableReferences(toJson(allRefs));
            }

            boolean hasPreRequestConfig = (monitor.getSignType() != null && !"NONE".equals(monitor.getSignType()))
                    || (monitor.getPreRequestScript() != null && !monitor.getPreRequestScript().isEmpty());
            if (hasPreRequestConfig) {
                try {
                    RequestContext reqCtx = new RequestContext(
                            monitor.getMethod() != null ? monitor.getMethod() : "GET",
                            url, headers,
                            variableEngine.replaceVariables(extractQueryString(url), baseVars),
                            body
                    );
                    ScriptResult scriptResult = preRequestScriptEngine.execute(monitor, reqCtx);
                    if (scriptResult.getAddHeaders() != null && !scriptResult.getAddHeaders().isEmpty()) {
                        StringBuilder headerBuilder = new StringBuilder(headers != null ? headers : "{}");
                        try {
                            com.fasterxml.jackson.databind.JsonNode existing = objectMapper.readTree(headers);
                            com.fasterxml.jackson.databind.node.ObjectNode objNode = (com.fasterxml.jackson.databind.node.ObjectNode) existing;
                            scriptResult.getAddHeaders().forEach(objNode::put);
                            headers = objectMapper.writeValueAsString(objNode);
                        } catch (Exception e) {
                            headers = toJson(scriptResult.getAddHeaders());
                        }
                    }
                    if (scriptResult.getRawBody() != null) {
                        body = scriptResult.getRawBody();
                    }
                    if (scriptResult.getAddParams() != null && !scriptResult.getAddParams().isEmpty()) {
                        url = appendQueryParams(url, scriptResult.getAddParams());
                        logEntry.setUrl(url);
                    }
                } catch (Exception e) {
                    log.error("Pre-request script execution failed for monitor: {}", monitor.getName(), e);
                }
            }

            try {
                URL parsedUrl = new URL(url);
                String host = parsedUrl.getHost();
                // Explicit SSRF check on the target host (OkHttp's Dns may skip IP literals)
                if (ssrfGuard != null) {
                    ssrfGuard.assertHostAllowed(host);
                }
                logEntry.setDomain(host);
                String ipAddress = resolveIpAddress(host);
                logEntry.setIpAddress(ipAddress);
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                log.warn("Failed to parse URL for domain/IP extraction: {}", url);
            }

            String httpMethod = monitor.getMethod() != null ? monitor.getMethod() : "GET";
            boolean isBodyMethod = "POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod);

            Request.Builder requestBuilder = new Request.Builder().url(url);
            if (headers != null && !headers.isEmpty()) {
                JsonNode headerNode = objectMapper.readTree(headers);
                headerNode.fields().forEachRemaining(entry -> {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue().asText());
                });
            }

            if (isBodyMethod) {
                String contentType = defaultContentType(monitor.getBodyType());
                if (headers != null && headers.contains("Content-Type")) {
                    JsonNode headerNode = objectMapper.readTree(headers);
                    if (headerNode.has("Content-Type")) {
                        contentType = headerNode.get("Content-Type").asText();
                    }
                }
                String requestBodyStr = (body != null && !body.isEmpty()) ? body : "";
                if ("form".equals(monitor.getBodyType())
                        && contentType.contains("application/x-www-form-urlencoded")
                        && requestBodyStr.startsWith("{")) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(requestBodyStr);
                        StringBuilder sb = new StringBuilder();
                        jsonNode.fields().forEachRemaining(entry -> {
                            if (sb.length() > 0) sb.append("&");
                            sb.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8));
                            sb.append("=");
                            sb.append(java.net.URLEncoder.encode(entry.getValue().asText(), java.nio.charset.StandardCharsets.UTF_8));
                        });
                        requestBodyStr = sb.toString();
                    } catch (Exception e) {
                        log.warn("Failed to convert form body to urlencoded: {}", e.getMessage());
                    }
                }
                RequestBody requestBody = RequestBody.create(requestBodyStr, MediaType.parse(contentType));
                requestBuilder.method(httpMethod, requestBody);
            } else {
                requestBuilder.method(httpMethod, null);
            }

            OkHttpClient client = getClient(timeout);
            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                long responseTime = System.currentTimeMillis() - startTime;
                logEntry.setStatusCode(response.code());
                logEntry.setResponseTime((int) responseTime);
                logEntry.setRequestHeaders(headers);
                logEntry.setRequestBody(body);

                if (response.body() != null) {
                    String responseBody = response.body().string();
                    logEntry.setResponseBody(responseBody);

                    ValidationResult validationResult = validateResponse(monitor, response.code(), responseBody);
                    logEntry.setStatus(validationResult.valid ? "SUCCESS" : "FAIL");
                    if (!validationResult.valid) {
                        logEntry.setErrorMessage(validationResult.errorMessage);
                    }

                    if (monitor.getVariableExtractConfig() != null && !monitor.getVariableExtractConfig().isEmpty()) {
                        Map<String, String> respHeaders = new LinkedHashMap<>();
                        for (String name : response.headers().names()) {
                            respHeaders.put(name.toLowerCase(), response.header(name));
                        }
                        Map<String, String> respCookies = new LinkedHashMap<>();
                        for (String setCookie : response.headers("Set-Cookie")) {
                            int eq = setCookie.indexOf('=');
                            if (eq > 0) {
                                String cookieName = setCookie.substring(0, eq).trim();
                                String cookieValue = setCookie.substring(eq + 1);
                                int semi = cookieValue.indexOf(';');
                                if (semi > 0) cookieValue = cookieValue.substring(0, semi);
                                respCookies.put(cookieName, cookieValue.trim());
                            }
                        }
                        Map<String, String[]> settingsTrace = variableEngine.extractVariablesWithTrace(
                                monitor.getVariableExtractConfig(), responseBody, respHeaders, respCookies, null);
                        if (!settingsTrace.isEmpty()) {
                            Map<String, String> settingsJson = new LinkedHashMap<>();
                            for (Map.Entry<String, String[]> entry : settingsTrace.entrySet()) {
                                if (!entry.getKey().startsWith("_")) {
                                    String jsonPath = entry.getValue().length > 0 ? entry.getValue()[0] : "";
                                    String value = entry.getValue().length > 1 ? entry.getValue()[1] : "";
                                    settingsJson.put(entry.getKey(), jsonPath + " => " + value);
                                }
                            }
                            if (!settingsJson.isEmpty()) {
                                logEntry.setVariableSettings(toJson(settingsJson));
                            }
                        }
                    }
                } else {
                    logEntry.setStatus("SUCCESS");
                }

                recordDomainAsset(url, response);
            }
        } catch (IOException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            logEntry.setResponseTime((int) responseTime);
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage(e.getMessage());
            log.error("HTTP monitor execution failed: {}", monitor.getName(), e);
        }
    }

    private ProxyConfig resolveActiveProxyConfig() {
        ProxyConfig config = proxyConfigService.getActiveProxyConfig();
        if (config == null) {
            proxyConfigService.refreshActiveProxy();
            config = proxyConfigService.getActiveProxyConfig();
        }
        return config;
    }

    private OkHttpClient getClient(int timeout) {
        ProxyConfig currentConfig = resolveActiveProxyConfig();
        String currentKey = currentConfig != null
                ? currentConfig.getId() + ":" + currentConfig.getProxyType() + ":" + currentConfig.getHost() + ":" + currentConfig.getPort()
                : "";
        String lastKey = lastUsedProxyConfig != null
                ? lastUsedProxyConfig.getId() + ":" + lastUsedProxyConfig.getProxyType() + ":" + lastUsedProxyConfig.getHost() + ":" + lastUsedProxyConfig.getPort()
                : "";
        boolean proxyChanged = !currentKey.equals(lastKey);

        if (sharedClient != null && !proxyChanged) {
            return sharedClient;
        }

        synchronized (this) {
            if (sharedClient != null && !proxyChanged) return sharedClient;

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
                    .followRedirects(true)
                    .followSslRedirects(true);

            if (ssrfGuard != null && ssrfGuard.isEnabled()) {
                builder.dns(hostname -> {
                    List<InetAddress> addrs = Dns.SYSTEM.lookup(hostname);
                    for (InetAddress a : addrs) {
                        ssrfGuard.assertAllowed(a);
                    }
                    return addrs;
                });
            }

            if (sslVerifyDisabled) {
                try {
                    TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        }
                    };
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                    SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                    builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                           .hostnameVerifier((hostname, session) -> true);
                } catch (Exception e) {
                    log.error("Failed to configure permissive SSL; keeping default verification", e);
                }
            }

            if (currentConfig != null) {
                Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(currentConfig.getProxyType()) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                java.net.InetSocketAddress proxyAddr = new java.net.InetSocketAddress(currentConfig.getHost(), currentConfig.getPort());
                builder.proxy(new Proxy(proxyType, proxyAddr));

                if (currentConfig.getUsername() != null && !currentConfig.getUsername().isEmpty()) {
                    final String proxyUser = currentConfig.getUsername();
                    final String proxyPass = currentConfig.getPassword() != null ? currentConfig.getPassword() : "";
                    builder.proxyAuthenticator((route, response) -> {
                        String credential = Credentials.basic(proxyUser, proxyPass);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    });
                }
            }

            sharedClient = builder.build();
            lastUsedProxyConfig = currentConfig;
        }
        return sharedClient;
    }

    private String resolveIpAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length > 0) {
                return addresses[0].getHostAddress();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve IP for host: {}", host);
        }
        return null;
    }

    private void recordDomainAsset(String url, Response response) {
        try {
            URL parsedUrl = new URL(url);
            String domain = parsedUrl.getHost();
            int port = parsedUrl.getPort() > 0 ? parsedUrl.getPort() :
                       "https".equals(parsedUrl.getProtocol()) ? 443 : 80;

            if ("https".equals(parsedUrl.getProtocol())) {
                sslService.checkSslCertificate(domain, port);
            }

            String key = domain + ":" + port;
            Long last = domainRecordTimes.get(key);
            long ttlMs = (long) domainRecordIntervalMinutes * 60_000L;
            if (last != null && System.currentTimeMillis() - last < ttlMs) {
                return;
            }
            domainRecordTimes.put(key, System.currentTimeMillis());

            String ipAddress = resolveIpAddress(domain);
            domainAssetService.recordDomain(domain, ipAddress, port);
        } catch (Exception e) {
            log.error("Failed to record domain asset", e);
        }
    }

    private static class ValidationResult {
        boolean valid;
        String errorMessage;
        ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
    }

    private ValidationResult validateResponse(Monitor monitor, int statusCode, String responseBody) {
        StringBuilder errors = new StringBuilder();
        if (monitor.getExpectedStatus() != null && statusCode != monitor.getExpectedStatus()) {
            errors.append("状态码不匹配: 期望 ").append(monitor.getExpectedStatus()).append(", 实际 ").append(statusCode);
        }
        if (monitor.getExpectedText() != null && !monitor.getExpectedText().isEmpty()) {
            if (responseBody == null || !responseBody.contains(monitor.getExpectedText())) {
                if (errors.length() > 0) errors.append("; ");
                errors.append("期望文本未找到: \"").append(monitor.getExpectedText()).append("\"");
            }
        }
        if (monitor.getJsonPath() != null && !monitor.getJsonPath().isEmpty()) {
            try {
                Object actual = JsonPath.read(responseBody, monitor.getJsonPath());
                if (monitor.getJsonExpected() != null && !monitor.getJsonExpected().isEmpty()) {
                    String expected = monitor.getJsonExpected();
                    String actualStr = actual != null ? actual.toString() : "";
                    if (!expected.equals(actualStr)) {
                        if (errors.length() > 0) errors.append("; ");
                        errors.append("JSONPath值不匹配: 期望 \"").append(expected).append("\", 实际 \"").append(actualStr).append("\"");
                    }
                }
            } catch (Exception e) {
                if (errors.length() > 0) errors.append("; ");
                errors.append("JSONPath表达式无效: ").append(monitor.getJsonPath());
            }
        }
        if (monitor.getExpectedRegex() != null && !monitor.getExpectedRegex().isEmpty()) {
            if (responseBody == null || !java.util.regex.Pattern.matches(monitor.getExpectedRegex(), responseBody)) {
                if (errors.length() > 0) errors.append("; ");
                errors.append("正则表达式不匹配: \"").append(monitor.getExpectedRegex()).append("\"");
            }
        }
        if (monitor.getMaxResponseBodySize() != null && monitor.getMaxResponseBodySize() > 0) {
            if (responseBody != null) {
                int bodySize = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (bodySize > monitor.getMaxResponseBodySize()) {
                    if (errors.length() > 0) errors.append("; ");
                    errors.append(String.format("响应体大小 %d bytes 超过阈值 %d bytes", bodySize, monitor.getMaxResponseBodySize()));
                }
            }
        }
        if (errors.length() > 0) {
            return new ValidationResult(false, errors.toString());
        }
        return new ValidationResult(true, null);
    }

    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private String toJson(Object obj) {
        try {
            return TRACE_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private String extractQueryString(String url) {
        try {
            URL parsed = new URL(url);
            String query = parsed.getQuery();
            return query != null ? query : "";
        } catch (Exception e) {
            int idx = url.indexOf('?');
            return idx >= 0 ? url.substring(idx + 1) : "";
        }
    }

    private String appendQueryParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) return url;
        StringBuilder sb = new StringBuilder(url);
        String separator = url.contains("?") ? "&" : "?";
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(separator).append(entry.getKey()).append("=").append(entry.getValue());
            separator = "&";
        }
        return sb.toString();
    }

    private String defaultContentType(String bodyType) {
        if (bodyType == null) return "application/json";
        switch (bodyType) {
            case "form":
                return "application/x-www-form-urlencoded";
            case "xml":
                return "application/xml";
            case "text":
                return "text/plain";
            case "json":
            default:
                return "application/json";
        }
    }
}
