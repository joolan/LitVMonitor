package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.DomainAsset;
import com.litv.monitor.entity.DomainIpHistory;
import com.litv.monitor.entity.SslCertificate;
import com.litv.monitor.mapper.DomainAssetMapper;
import com.litv.monitor.mapper.DomainIpHistoryMapper;
import com.litv.monitor.mapper.SslCertificateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainAssetService {

    private final DomainAssetMapper domainAssetMapper;
    private final DomainIpHistoryMapper domainIpHistoryMapper;
    private final SslCertificateMapper sslCertificateMapper;

    public void recordDomain(String domain, String ipAddress, int port) {
        LambdaQueryWrapper<DomainAsset> wrapper = new LambdaQueryWrapper<DomainAsset>()
                .eq(DomainAsset::getDomain, domain)
                .eq(DomainAsset::getPort, port);
        DomainAsset existing = domainAssetMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setIpAddress(ipAddress);
            existing.setLastSeenAt(LocalDateTime.now());
            existing.setIsAlive(true);
            domainAssetMapper.updateById(existing);
        } else {
            DomainAsset asset = new DomainAsset();
            asset.setDomain(domain);
            asset.setIpAddress(ipAddress);
            asset.setPort(port);
            asset.setFirstSeenAt(LocalDateTime.now());
            asset.setLastSeenAt(LocalDateTime.now());
            asset.setMonitorCount(1);
            asset.setIsAlive(true);
            domainAssetMapper.insert(asset);
        }

        recordIpHistory(domain, ipAddress, port);
    }

    private void recordIpHistory(String domain, String ipAddress, int port) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DomainIpHistory> wrapper = new LambdaQueryWrapper<DomainIpHistory>()
                .eq(DomainIpHistory::getDomain, domain)
                .eq(DomainIpHistory::getIpAddress, ipAddress)
                .eq(DomainIpHistory::getPort, port);
        DomainIpHistory existing = domainIpHistoryMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setLastSeenAt(LocalDateTime.now());
            domainIpHistoryMapper.updateById(existing);
        } else {
            DomainIpHistory history = new DomainIpHistory();
            history.setDomain(domain);
            history.setIpAddress(ipAddress);
            history.setPort(port);
            history.setFirstSeenAt(LocalDateTime.now());
            history.setLastSeenAt(LocalDateTime.now());
            domainIpHistoryMapper.insert(history);
        }
    }

    public List<String> getDomainIpHistory(String domain, Integer port) {
        LambdaQueryWrapper<DomainIpHistory> wrapper = new LambdaQueryWrapper<DomainIpHistory>()
                .eq(DomainIpHistory::getDomain, domain)
                .orderByDesc(DomainIpHistory::getLastSeenAt);
        if (port != null) {
            wrapper.eq(DomainIpHistory::getPort, port);
        }
        return domainIpHistoryMapper.selectList(wrapper).stream()
                .map(DomainIpHistory::getIpAddress)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<DomainAsset> listDomainAssets(String keyword, String sslStatus, Integer maxRemainingDays) {
        LambdaQueryWrapper<DomainAsset> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(DomainAsset::getDomain, keyword)
                    .or()
                    .like(DomainAsset::getIpAddress, keyword);
        }
        wrapper.orderByDesc(DomainAsset::getLastSeenAt);
        List<DomainAsset> domains = domainAssetMapper.selectList(wrapper);

        for (DomainAsset domain : domains) {
            SslCertificate latestSsl = sslCertificateMapper.selectOne(
                    new LambdaQueryWrapper<SslCertificate>()
                            .eq(SslCertificate::getDomain, domain.getDomain())
                            .eq(SslCertificate::getPort, domain.getPort())
                            .orderByDesc(SslCertificate::getCheckedAt)
                            .last("LIMIT 1")
            );
            if (latestSsl != null) {
                domain.setSslRemainingDays(latestSsl.getRemainingDays());
                domain.setSslStatus(latestSsl.getStatus());
                domain.setSslNotAfter(latestSsl.getNotAfter());
            }
        }

        if (sslStatus != null && !sslStatus.isEmpty()) {
            domains = domains.stream()
                    .filter(d -> sslStatus.equals(d.getSslStatus()))
                    .toList();
        }

        if (maxRemainingDays != null) {
            domains = domains.stream()
                    .filter(d -> d.getSslRemainingDays() != null && d.getSslRemainingDays() <= maxRemainingDays)
                    .toList();
        }

        return domains;
    }

    public DomainAsset getDomainAssetById(Long id) {
        return domainAssetMapper.selectById(id);
    }

    public boolean deleteDomainAsset(Long id) {
        return domainAssetMapper.deleteById(id) > 0;
    }

    public DomainAsset updateDomainAsset(Long id, DomainAsset updateData) {
        DomainAsset existing = domainAssetMapper.selectById(id);
        if (existing == null) {
            return null;
        }

        if (updateData.getSslAlertEnabled() != null) {
            existing.setSslAlertEnabled(updateData.getSslAlertEnabled());
        }
        if (updateData.getSslAlertConfigIds() != null) {
            existing.setSslAlertConfigIds(updateData.getSslAlertConfigIds());
        }
        if (updateData.getSslAlertDaysBefore() != null) {
            existing.setSslAlertDaysBefore(updateData.getSslAlertDaysBefore());
        }
        if (updateData.getSslAlertOneDayBefore() != null) {
            existing.setSslAlertOneDayBefore(updateData.getSslAlertOneDayBefore());
        }
        if (updateData.getSslAlertOnExecute() != null) {
            existing.setSslAlertOnExecute(updateData.getSslAlertOnExecute());
        }

        domainAssetMapper.updateById(existing);
        return existing;
    }

    public Long countDomains() {
        return domainAssetMapper.selectCount(null);
    }

}
