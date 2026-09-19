package com.litv.monitor.service.script;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class HmacSha256SignFunction implements SignFunction {

    @Override
    public String sign(RequestContext ctx, JsonNode config) {
        try {
            String secretKey = config.has("secretKey") ? config.get("secretKey").asText() : "";
            String algorithm = config.has("algorithm") ? config.get("algorithm").asText() : "HmacSHA256";
            String timestampParam = config.has("timestampParam") ? config.get("timestampParam").asText() : "timestamp";
            String includeTimestamp = config.has("includeTimestamp") ? config.get("includeTimestamp").asText() : "true";
            String canonicalStyle = config.has("canonicalStyle") ? config.get("canonicalStyle").asText() : "aws";

            String canonical;
            if ("aws".equals(canonicalStyle)) {
                canonical = buildAwsCanonical(ctx, config, timestampParam, includeTimestamp);
            } else {
                canonical = buildSortedCanonical(ctx, timestampParam, includeTimestamp);
            }

            log.debug("HMAC-SHA256 sign canonical: {}", canonical);

            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] hash = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));

            String signEncoding = config.has("signEncoding") ? config.get("signEncoding").asText() : "base64";
            if ("hex".equals(signEncoding)) {
                return bytesToHex(hash);
            }
            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            log.error("HMAC-SHA256 sign error", e);
            return "";
        }
    }

    private String buildAwsCanonical(RequestContext ctx, JsonNode config, String timestampParam, String includeTimestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getMethod()).append("\n");

        // Path from URL
        String url = ctx.getUrl();
        try {
            java.net.URL parsed = new java.net.URL(url);
            sb.append(parsed.getPath()).append("\n");
        } catch (Exception e) {
            sb.append("/\n");
        }

        // Timestamp
        if ("true".equalsIgnoreCase(includeTimestamp)) {
            sb.append(ctx.getTimestampS()).append("\n");
        }

        // SHA256 of body
        if (ctx.getBody() != null && !ctx.getBody().isEmpty()) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] bodyHash = digest.digest(ctx.getBody().getBytes(StandardCharsets.UTF_8));
                sb.append(bytesToHex(bodyHash));
            } catch (Exception e) {
                sb.append("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
            }
        } else {
            sb.append("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        }

        return sb.toString();
    }

    private String buildSortedCanonical(RequestContext ctx, String timestampParam, String includeTimestamp) {
        TreeMap<String, String> params = new TreeMap<>();
        if (ctx.getParams() != null) {
            params.putAll(ctx.getParams());
        }
        if (ctx.getBody() != null && !ctx.getBody().isEmpty()) {
            try {
                JsonNode bodyNode = getMapper().readTree(ctx.getBody());
                if (bodyNode.isObject()) {
                    bodyNode.fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
                }
            } catch (Exception e) {
                // skip
            }
        }
        if ("true".equalsIgnoreCase(includeTimestamp)) {
            params.put(timestampParam, ctx.getTimestampS());
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
