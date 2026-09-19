package com.litv.monitor.service.script;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Host object exposed to pre-request scripts as {@code __rt}.
 * Only these specific helpers are reachable from the sandbox, arbitrary Java
 * access (e.g. {@code java.*}) is intentionally not available.
 */
public class ScriptRuntime {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String NONCE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public String md5(String str) {
        return hex(digest("MD5", bytes(str)));
    }

    public String md5Hex(String str) {
        return md5(str);
    }

    public String sha1(String str) {
        return hex(digest("SHA-1", bytes(str)));
    }

    public String sha256(String str) {
        return hex(digest("SHA-256", bytes(str)));
    }

    public String hmacSha256(String str, String key) {
        return hex(hmac("HmacSHA256", str, key));
    }

    public String hmacSha256Base64(String str, String key) {
        return Base64.getEncoder().encodeToString(hmac("HmacSHA256", str, key));
    }

    public String hmacMd5(String str, String key) {
        return hex(hmac("HmacMD5", str, key));
    }

    public String base64(String str) {
        return Base64.getEncoder().encodeToString(bytes(str));
    }

    public String base64Decode(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }

    public String rsaSha256Sign(String str, String privateKeyPem) {
        return Base64.getEncoder().encodeToString(rsaSign(str, privateKeyPem));
    }

    public String rsaSha256SignHex(String str, String privateKeyPem) {
        return hex(rsaSign(str, privateKeyPem));
    }

    public long timestamp() {
        return System.currentTimeMillis();
    }

    public long timestampS() {
        return System.currentTimeMillis() / 1000;
    }

    public String datetime() {
        return LocalDateTime.now(ZONE).format(DATETIME);
    }

    public String time() {
        return LocalDateTime.now(ZONE).format(TIME);
    }

    public String date() {
        return LocalDateTime.now(ZONE).format(DATE);
    }

    public String nonce(Integer len) {
        int n = len != null ? len : 32;
        StringBuilder sb = new StringBuilder(n);
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            sb.append(NONCE_CHARS.charAt(rnd.nextInt(NONCE_CHARS.length())));
        }
        return sb.toString();
    }

    public String uuid() {
        return UUID.randomUUID().toString();
    }

    public String uuidShort() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public long random(Integer max) {
        int bound = (max != null && max > 0) ? max : 1000000;
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
    }

    private byte[] rsaSign(String str, String privateKeyPem) {
        try {
            String pem = privateKeyPem
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(bytes(str));
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException("RSA签名失败: " + e.getMessage(), e);
        }
    }

    private byte[] hmac(String algorithm, String str, String key) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(bytes(key), algorithm));
            return mac.doFinal(bytes(str));
        } catch (Exception e) {
            throw new RuntimeException("HMAC计算失败: " + e.getMessage(), e);
        }
    }

    private byte[] digest(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (Exception e) {
            throw new RuntimeException("摘要计算失败: " + e.getMessage(), e);
        }
    }

    private static byte[] bytes(String str) {
        return (str != null ? str : "").getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] data) {
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (byte b : data) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
