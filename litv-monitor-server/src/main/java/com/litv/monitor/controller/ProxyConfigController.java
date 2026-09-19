package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.ProxyConfig;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.ProxyConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class ProxyConfigController {

    private final ProxyConfigService proxyConfigService;
    private final AuditLogService auditLogService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ProxyConfig>> list() {
        return Result.success(proxyConfigService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProxyConfig> getById(@PathVariable Long id) {
        ProxyConfig config = proxyConfigService.getById(id);
        return config != null ? Result.success(config) : Result.error(404, "代理配置不存在");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProxyConfig> create(@Valid @RequestBody ProxyConfig config) {
        ProxyConfig created = proxyConfigService.create(config);
        auditLog();
        return Result.success(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProxyConfig> update(@PathVariable Long id, @Valid @RequestBody ProxyConfig config) {
        ProxyConfig updated = proxyConfigService.update(id, config);
        if (updated == null) return Result.error(404, "代理配置不存在");
        auditLog();
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        proxyConfigService.delete(id);
        auditLog();
        return Result.success();
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProxyConfig> activate(@PathVariable Long id) {
        ProxyConfig config = proxyConfigService.setActive(id);
        auditLog();
        return Result.success(config);
    }

    @PutMapping("/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deactivateAll() {
        proxyConfigService.deactivateAll();
        auditLog();
        return Result.success();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> test(@PathVariable Long id) {
        ProxyConfig config = proxyConfigService.getById(id);
        if (config == null) return Result.error(404, "代理配置不存在");

        try {
            java.net.Proxy.Type proxyType = "SOCKS5".equals(config.getProxyType()) ? java.net.Proxy.Type.SOCKS : java.net.Proxy.Type.HTTP;
            java.net.InetSocketAddress proxyAddr = new InetSocketAddress(config.getHost(), config.getPort());

            java.net.ProxySelector proxySelector = new java.net.ProxySelector() {
                @Override
                public java.util.List<java.net.Proxy> select(URI uri) {
                    return java.util.List.of(new java.net.Proxy(proxyType, proxyAddr));
                }
                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException e) {}
            };

            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .proxy(proxySelector)
                    .connectTimeout(Duration.ofSeconds(10));

            if (config.getUsername() != null && !config.getUsername().isEmpty()) {
                clientBuilder.authenticator(new java.net.Authenticator() {
                    @Override
                    protected java.net.PasswordAuthentication getPasswordAuthentication() {
                        return new java.net.PasswordAuthentication(config.getUsername(),
                                (config.getPassword() != null ? config.getPassword() : "").toCharArray());
                    }
                });
            }

            HttpClient client = clientBuilder.build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://httpbin.org/ip"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                auditLog();
                return Result.success("代理连接成功! 响应: " + response.body().substring(0, Math.min(200, response.body().length())));
            } else {
                return Result.error("代理连接异常，HTTP 状态码: " + response.statusCode());
            }
        } catch (Exception e) {
            return Result.error("代理连接失败: " + e.getMessage());
        }
    }

    private void auditLog() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        auditLogService.record(null, username, "UPDATE", "PROXY", null, "代理配置", "更新代理配置", null);
    }
}
