package com.litv.monitor.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret:}")
    private String configuredSecret;

    @Value("${jwt.secret-file:./.jwt-secret}")
    private String secretFile;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private String resolvedSecret;

    @PostConstruct
    public void init() {
        if (configuredSecret != null && !configuredSecret.isEmpty()) {
            if (configuredSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "JWT_SECRET 强度不足：至少需要 " + MIN_SECRET_LENGTH + " 个字符，请使用足够随机的密钥");
            }
            resolvedSecret = configuredSecret;
            log.info("JWT secret loaded from JWT_SECRET environment variable");
            return;
        }
        resolvedSecret = loadOrCreateSecretFile();
    }

    private String loadOrCreateSecretFile() {
        Path path = Paths.get(secretFile).toAbsolutePath().normalize();
        try {
            if (Files.exists(path)) {
                String existing = Files.readString(path).trim();
                if (existing.length() >= MIN_SECRET_LENGTH) {
                    log.info("JWT secret loaded from {}", path);
                    return existing;
                }
            }
            byte[] buf = new byte[48];
            new SecureRandom().nextBytes(buf);
            String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
            Files.writeString(path, generated,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                path.toFile().setReadable(false, false);
                path.toFile().setReadable(true, true);
            } catch (Exception ignored) {
                // best effort permission tightening
            }
            log.warn("JWT_SECRET 未设置，已生成随机密钥并保存到 {}。生产环境建议通过环境变量 JWT_SECRET 配置并妥善备份该文件。", path);
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("无法读取或生成 JWT 密钥文件: " + secretFile, e);
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(resolvedSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        return generateToken(authentication, UUID.randomUUID().toString());
    }

    public String generateToken(Authentication authentication, String jti) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String role = userDetails.getAuthorities().stream()
                .findFirst().map(GrantedAuthority::getAuthority).orElse("ROLE_VIEWER");
        return generateToken(userDetails.getUsername(), role, false, jti);
    }

    public String generateToken(String username, String role, boolean mustChangePassword, String jti) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("mcp", mustChangePassword)
                .claim("jti", jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isMustChangePassword(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Boolean.TRUE.equals(claims.get("mcp", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String generateToken(String username) {
        return generateToken(username, UUID.randomUUID().toString());
    }

    public String generateToken(String username, String jti) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("jti", jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }

    public String getJtiFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("jti", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
