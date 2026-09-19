package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.AlertConfig;
import com.litv.monitor.entity.AlertChannel;
import com.litv.monitor.entity.DomainAsset;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.SslCertificate;
import com.litv.monitor.mapper.AlertConfigMapper;
import com.litv.monitor.mapper.AlertChannelMapper;
import com.litv.monitor.mapper.DomainAssetMapper;
import com.litv.monitor.mapper.SslCertificateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SslService {

    private final SslCertificateMapper sslCertificateMapper;
    private final DomainAssetMapper domainAssetMapper;
    private final AlertConfigMapper alertConfigMapper;
    private final AlertChannelMapper alertChannelMapper;
    private final AlertService alertService;

    @Value("${monitor.ssl-check-days-warning:30}")
    private int sslWarningDays;

    @Value("${monitor.ssl-check-interval-minutes:360}")
    private int sslCheckIntervalMinutes;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> sslCheckTimes = new java.util.concurrent.ConcurrentHashMap<>();

    public void checkSslCertificate(String domain, int port) {
        String key = domain + ":" + port;
        Long last = sslCheckTimes.get(key);
        long ttlMs = (long) sslCheckIntervalMinutes * 60_000L;
        if (last != null && System.currentTimeMillis() - last < ttlMs) {
            return;
        }
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket();
            socket.connect(new java.net.InetSocketAddress(domain, port), 10000);
            socket.startHandshake();

            java.security.cert.Certificate[] certs = socket.getSession().getPeerCertificates();
            if (certs.length > 0) {
                X509Certificate cert = (X509Certificate) certs[0];
                saveCertificate(domain, port, cert);
                // 执行时告警：证书过期或证书错误时按配置触发
                checkExecutionSslAlert(domain, port);
            }

            socket.close();
            sslCheckTimes.put(key, System.currentTimeMillis());
        } catch (Exception e) {
            log.error("SSL check failed for {}:{}", domain, port, e);
        }
    }

    private void checkExecutionSslAlert(String domain, int port) {
        try {
            LambdaQueryWrapper<DomainAsset> wrapper = new LambdaQueryWrapper<DomainAsset>()
                    .eq(DomainAsset::getDomain, domain)
                    .eq(DomainAsset::getPort, port);
            DomainAsset domainAsset = domainAssetMapper.selectOne(wrapper);
            if (domainAsset == null) {
                return;
            }
            if (!Boolean.TRUE.equals(domainAsset.getSslAlertEnabled())
                    || !Boolean.TRUE.equals(domainAsset.getSslAlertOnExecute())) {
                return;
            }

            List<SslCertificate> certificates = sslCertificateMapper.selectList(
                    new LambdaQueryWrapper<SslCertificate>()
                            .eq(SslCertificate::getDomain, domain)
                            .eq(SslCertificate::getPort, port)
                            .orderByDesc(SslCertificate::getCheckedAt)
                            .last("LIMIT 1")
            );
            if (certificates.isEmpty()) {
                return;
            }

            SslCertificate cert = certificates.get(0);
            String reason = null;
            if ("MISMATCH".equals(cert.getStatus())) {
                reason = String.format("SSL证书与域名不匹配！域名: %s，证书主体: %s", domain, cert.getSubject());
            } else if (cert.getRemainingDays() != null && cert.getRemainingDays() <= 0) {
                reason = String.format("SSL证书已过期！域名: %s，过期时间: %s", domain, cert.getNotAfter());
            }
            if (reason != null) {
                log.info("Execution-time SSL alert triggered for {}:{}: {}", domain, port, reason);
                sendSslAlert(domainAsset, reason, cert);
            }
        } catch (Exception e) {
            log.error("Failed to check execution SSL alert for {}:{}", domain, port, e);
        }
    }

    private void saveCertificate(String domain, int port, X509Certificate cert) {
        LocalDateTime notBefore = cert.getNotBefore().toInstant()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        LocalDateTime notAfter = cert.getNotAfter().toInstant()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        int remainingDays = (int) ((cert.getNotAfter().getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24));
        boolean domainMatch = verifyDomainMatchesCertificate(domain, cert);
        String status = computeStatus(remainingDays, domainMatch);

        // Find or create domain asset
        LambdaQueryWrapper<DomainAsset> domainWrapper = new LambdaQueryWrapper<DomainAsset>()
                .eq(DomainAsset::getDomain, domain)
                .eq(DomainAsset::getPort, port);
        DomainAsset domainAsset = domainAssetMapper.selectOne(domainWrapper);

        if (domainAsset == null) {
            domainAsset = new DomainAsset();
            domainAsset.setDomain(domain);
            domainAsset.setPort(port);
            domainAsset.setFirstSeenAt(LocalDateTime.now());
            domainAsset.setLastSeenAt(LocalDateTime.now());
            domainAsset.setIsAlive(true);
            domainAssetMapper.insert(domainAsset);
        }

        // Check if certificate already exists
        LambdaQueryWrapper<SslCertificate> sslWrapper = new LambdaQueryWrapper<SslCertificate>()
                .eq(SslCertificate::getDomain, domain)
                .eq(SslCertificate::getPort, port);
        SslCertificate existing = sslCertificateMapper.selectOne(sslWrapper);

        if (existing != null) {
            existing.setIssuer(cert.getIssuerX500Principal().getName());
            existing.setSubject(cert.getSubjectX500Principal().getName());
            existing.setSerialNumber(cert.getSerialNumber().toString(16));
            existing.setNotBefore(notBefore);
            existing.setNotAfter(notAfter);
            existing.setRemainingDays(remainingDays);
            existing.setFingerprint(getFingerprint(cert));
            existing.setIsValid(remainingDays > 0 && domainMatch);
            existing.setStatus(status);
            existing.setCheckedAt(LocalDateTime.now());
            sslCertificateMapper.updateById(existing);
        } else {
            SslCertificate sslCert = new SslCertificate();
            sslCert.setDomainAssetId(domainAsset.getId());
            sslCert.setDomain(domain);
            sslCert.setPort(port);
            sslCert.setIssuer(cert.getIssuerX500Principal().getName());
            sslCert.setSubject(cert.getSubjectX500Principal().getName());
            sslCert.setSerialNumber(cert.getSerialNumber().toString(16));
            sslCert.setNotBefore(notBefore);
            sslCert.setNotAfter(notAfter);
            sslCert.setRemainingDays(remainingDays);
            sslCert.setFingerprint(getFingerprint(cert));
            sslCert.setIsValid(remainingDays > 0 && domainMatch);
            sslCert.setStatus(status);
            sslCert.setCheckedAt(LocalDateTime.now());
            sslCertificateMapper.insert(sslCert);
        }
    }

    private String computeStatus(int remainingDays, boolean domainMatch) {
        if (remainingDays <= 0) {
            return "EXPIRED";
        }
        if (!domainMatch) {
            return "MISMATCH";
        }
        return "VALID";
    }

    private boolean verifyDomainMatchesCertificate(String domain, X509Certificate cert) {
        try {
            java.util.Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
            if (altNames == null || altNames.isEmpty()) {
                return false;
            }
            String normalizedDomain = domain.toLowerCase();
            for (List<?> entry : altNames) {
                if (entry == null || entry.size() < 2) continue;
                Object typeObj = entry.get(0);
                if (!(typeObj instanceof Integer) || (Integer) typeObj != 2) continue; // dNSName
                String dnsName = String.valueOf(entry.get(1)).toLowerCase();
                if (dnsName.equals(normalizedDomain)) {
                    return true;
                }
                // wildcard: *.example.com only matches one subdomain level
                if (dnsName.startsWith("*.")) {
                    String suffix = dnsName.substring(1); // ".example.com"
                    if (normalizedDomain.endsWith(suffix)) {
                        String prefix = normalizedDomain.substring(0, normalizedDomain.length() - suffix.length());
                        if (!prefix.isEmpty() && !prefix.contains(".")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to read certificate SAN for domain {}", domain, e);
            return false;
        }
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void checkSslAlerts() {
        log.info("Starting SSL certificate alert check...");

        List<DomainAsset> alertEnabledDomains = domainAssetMapper.selectList(
                new LambdaQueryWrapper<DomainAsset>()
                        .eq(DomainAsset::getSslAlertEnabled, true)
        );

        for (DomainAsset domain : alertEnabledDomains) {
            try {
                checkDomainSslAlert(domain);
            } catch (Exception e) {
                log.error("Failed to check SSL alert for domain: {}", domain.getDomain(), e);
            }
        }
    }

    private void checkDomainSslAlert(DomainAsset domain) {
        List<SslCertificate> certificates = sslCertificateMapper.selectList(
                new LambdaQueryWrapper<SslCertificate>()
                        .eq(SslCertificate::getDomain, domain.getDomain())
                        .eq(SslCertificate::getPort, domain.getPort())
                        .orderByDesc(SslCertificate::getCheckedAt)
                        .last("LIMIT 1")
        );

        if (certificates.isEmpty()) {
            return;
        }

        SslCertificate cert = certificates.get(0);
        int remainingDays = cert.getRemainingDays() != null ? cert.getRemainingDays() : 0;
        int alertDaysBefore = domain.getSslAlertDaysBefore() != null ? domain.getSslAlertDaysBefore() : 30;
        boolean alertOneDayBefore = domain.getSslAlertOneDayBefore() != null ? domain.getSslAlertOneDayBefore() : true;

        boolean shouldAlert = false;
        String alertReason = "";

        if ("MISMATCH".equals(cert.getStatus())) {
            shouldAlert = true;
            alertReason = String.format("SSL证书与域名不匹配！域名: %s，证书主体: %s", domain.getDomain(), cert.getSubject());
        } else if (remainingDays <= 0) {
            shouldAlert = true;
            alertReason = String.format("SSL证书已过期！域名: %s，过期时间: %s", domain.getDomain(), cert.getNotAfter());
        } else if (remainingDays <= alertDaysBefore) {
            shouldAlert = true;
            alertReason = String.format("SSL证书将在%d天后过期。域名: %s，过期时间: %s", remainingDays, domain.getDomain(), cert.getNotAfter());
        } else if (alertOneDayBefore && remainingDays == 1) {
            shouldAlert = true;
            alertReason = String.format("SSL证书明天将过期！域名: %s，过期时间: %s", domain.getDomain(), cert.getNotAfter());
        }

        if (shouldAlert) {
            sendSslAlert(domain, alertReason, cert);
        }
    }

    private void sendSslAlert(DomainAsset domain, String reason, SslCertificate cert) {
        ExecutionLog executionLog = new ExecutionLog();
        executionLog.setMonitorId(0L);
        executionLog.setDomain(domain.getDomain());
        executionLog.setStatus("SSL_CERT_ALERT");
        executionLog.setStatusCode(0);
        executionLog.setErrorMessage(reason);
        executionLog.setExecutedAt(LocalDateTime.now());

        String configIds = domain.getSslAlertConfigIds();
        if (configIds != null && !configIds.isEmpty()) {
            alertService.sendAlertByIds(configIds, executionLog, "SSL_CERT");
        } else {
            alertService.recordAlertLogForSsl(executionLog, reason);
        }
    }

    public List<SslCertificate> getExpiringCertificates() {
        return sslCertificateMapper.selectList(
                new LambdaQueryWrapper<SslCertificate>()
                        .le(SslCertificate::getRemainingDays, sslWarningDays)
                        .gt(SslCertificate::getRemainingDays, 0)
                        .orderByAsc(SslCertificate::getRemainingDays)
        );
    }

    public List<SslCertificate> listSslCertificates(String domain) {
        LambdaQueryWrapper<SslCertificate> wrapper = new LambdaQueryWrapper<>();
        if (domain != null && !domain.isEmpty()) {
            wrapper.eq(SslCertificate::getDomain, domain);
        }
        wrapper.orderByAsc(SslCertificate::getRemainingDays);
        return sslCertificateMapper.selectList(wrapper);
    }

    public Long countSslExpiring() {
        return sslCertificateMapper.selectCount(
                new LambdaQueryWrapper<SslCertificate>()
                        .le(SslCertificate::getRemainingDays, sslWarningDays)
                        .gt(SslCertificate::getRemainingDays, 0)
        );
    }

    public Long countSslExpired() {
        return sslCertificateMapper.selectCount(
                new LambdaQueryWrapper<SslCertificate>()
                        .le(SslCertificate::getRemainingDays, 0)
        );
    }

    public Long countSslMismatch() {
        return sslCertificateMapper.selectCount(
                new LambdaQueryWrapper<SslCertificate>()
                        .eq(SslCertificate::getStatus, "MISMATCH")
        );
    }

    private String getFingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] publicKeyBytes = cert.getPublicKey().getEncoded();
            byte[] digest = md.digest(publicKeyBytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(":");
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
