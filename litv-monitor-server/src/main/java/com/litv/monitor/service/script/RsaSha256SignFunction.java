package com.litv.monitor.service.script;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class RsaSha256SignFunction implements SignFunction {

    @Override
    public String sign(RequestContext ctx, JsonNode config) {
        try {
            String privateKeyPem = config.has("privateKey") ? config.get("privateKey").asText() : "";
            String timestampParam = config.has("timestampParam") ? config.get("timestampParam").asText() : "timestamp";
            String includeTimestamp = config.has("includeTimestamp") ? config.get("includeTimestamp").asText() : "true";

            // Build canonical string (same as HMAC sorted style)
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
            String canonical = sb.toString();
            log.debug("RSA-SHA256 sign canonical: {}", canonical);

            // Parse private key
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            if (privateKey == null) {
                log.error("Failed to parse RSA private key");
                return "";
            }

            // Sign
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();

            String signEncoding = config.has("signEncoding") ? config.get("signEncoding").asText() : "base64";
            if ("hex".equals(signEncoding)) {
                return bytesToHex(signed);
            }
            return Base64.getEncoder().encodeToString(signed);

        } catch (Exception e) {
            log.error("RSA-SHA256 sign error", e);
            return "";
        }
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(clean);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            log.error("Failed to parse private key: {}", e.getMessage());
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
