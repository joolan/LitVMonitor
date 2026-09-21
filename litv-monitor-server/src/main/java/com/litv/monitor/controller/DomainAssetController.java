package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.DomainAsset;
import com.litv.monitor.entity.SslCertificate;
import com.litv.monitor.mapper.DomainAssetMapper;
import com.litv.monitor.service.DomainAssetService;
import com.litv.monitor.service.SslService;
import com.litv.monitor.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/domain")
@RequiredArgsConstructor
public class DomainAssetController {

    private final DomainAssetService domainAssetService;
    private final DomainAssetMapper domainAssetMapper;
    private final SslService sslService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/list")
    public Result<List<DomainAsset>> listDomains(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sslStatus,
            @RequestParam(required = false) Integer maxRemainingDays,
            @RequestParam(required = false) Boolean starred) {
        return Result.success(domainAssetService.listDomainAssets(keyword, sslStatus, maxRemainingDays, starred));
    }

    @GetMapping("/{id}")
    public Result<DomainAsset> getDomain(@PathVariable Long id) {
        DomainAsset asset = domainAssetService.getDomainAssetById(id);
        return asset != null ? Result.success(asset) : Result.error(404, "域名证书不存在");
    }

    @GetMapping("/{id}/ip-history")
    public Result<List<String>> getDomainIpHistory(@PathVariable Long id) {
        DomainAsset asset = domainAssetService.getDomainAssetById(id);
        if (asset == null) {
            return Result.error(404, "域名证书不存在");
        }
        return Result.success(domainAssetService.getDomainIpHistory(asset.getDomain(), asset.getPort()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<DomainAsset> updateDomain(@PathVariable Long id, @RequestBody DomainAsset updateData) {
        DomainAsset result = domainAssetService.updateDomainAsset(id, updateData);
        if (result != null) {
            String username = getCurrentUsername();
            String requestBody = "";
            try { requestBody = objectMapper.writeValueAsString(updateData); } catch (Exception ignored) {}
            auditLogService.record(null, username, "UPDATE", "DOMAIN",
                    String.valueOf(id), result.getDomain(), "更新域名资产", requestBody);
        }
        return result != null ? Result.success(result) : Result.error(404, "域名证书不存在");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteDomain(@PathVariable Long id) {
        domainAssetService.deleteDomainAsset(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "DOMAIN",
                String.valueOf(id), "", "删除域名资产", null);
        return Result.success();
    }

    @GetMapping("/{id}/ssl")
    public Result<List<SslCertificate>> getSslCertificates(@PathVariable Long id) {
        DomainAsset asset = domainAssetService.getDomainAssetById(id);
        if (asset == null) {
            return Result.error(404, "域名证书不存在");
        }
        return Result.success(sslService.listSslCertificates(asset.getDomain()));
    }

    @GetMapping("/ssl/expiring")
    public Result<List<SslCertificate>> getExpiringCertificates() {
        return Result.success(sslService.getExpiringCertificates());
    }

    @PutMapping("/{id}/star")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<DomainAsset> toggleStar(@PathVariable Long id) {
        DomainAsset domain = domainAssetMapper.selectById(id);
        if (domain == null) return Result.error(404, "域名不存在");
        domain.setStarred(domain.getStarred() != null && domain.getStarred() ? false : true);
        domainAssetMapper.updateById(domain);
        return Result.success(domain);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
