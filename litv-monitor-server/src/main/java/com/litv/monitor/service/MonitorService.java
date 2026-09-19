package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.MonitorDTO;
import com.litv.monitor.dto.MonitorVO;
import com.litv.monitor.entity.GroupMonitor;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.entity.MonitorGroup;
import com.litv.monitor.mapper.GroupMonitorMapper;
import com.litv.monitor.mapper.MonitorGroupMapper;
import com.litv.monitor.mapper.MonitorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorMapper monitorMapper;
    private final GroupMonitorMapper groupMonitorMapper;
    private final MonitorGroupMapper monitorGroupMapper;

    public Page<MonitorVO> listMonitors(Page<Monitor> page, String keyword, Boolean enabled, Long id) {
        LambdaQueryWrapper<Monitor> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            wrapper.eq(Monitor::getId, id);
        } else if (keyword != null && !keyword.isEmpty()) {
            // 必须把 OR 分组，否则与后面的 enabled 条件组合会变成
            // name LIKE ? OR (url LIKE ? AND enabled = ?)，导致 enabled 失效
            wrapper.and(w -> w.like(Monitor::getName, keyword)
                    .or()
                    .like(Monitor::getUrl, keyword));
        }
        if (enabled != null) {
            wrapper.eq(Monitor::getEnabled, enabled);
        }
        wrapper.orderByDesc(Monitor::getCreatedAt);
        Page<Monitor> monitorPage = monitorMapper.selectPage(page, wrapper);

        Map<Long, Long> counts = getGroupCounts();

        Page<MonitorVO> voPage = new Page<>(monitorPage.getCurrent(), monitorPage.getSize(), monitorPage.getTotal());
        voPage.setRecords(monitorPage.getRecords().stream().map(m -> {
            MonitorVO vo = new MonitorVO();
            org.springframework.beans.BeanUtils.copyProperties(m, vo);
            vo.setGroupCount(counts.getOrDefault(m.getId(), 0L));
            return vo;
        }).toList());
        return voPage;
    }

    public Monitor getMonitorById(Long id) {
        return monitorMapper.selectById(id);
    }

    public Monitor createMonitor(MonitorDTO dto) {
        Monitor monitor = new Monitor();
        BeanUtils.copyProperties(dto, monitor);
        monitorMapper.insert(monitor);

        if (dto.getResponseTimeAlertEnabled() != null && dto.getResponseTimeAlertEnabled()) {
            LambdaUpdateWrapper<Monitor> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Monitor::getId, monitor.getId());
            wrapper.set(Monitor::getResponseTimeAlertEnabled, dto.getResponseTimeAlertEnabled());
            wrapper.set(Monitor::getResponseTimeThreshold, dto.getResponseTimeThreshold());
            wrapper.set(Monitor::getResponseTimeConsecutiveCount, dto.getResponseTimeConsecutiveCount());
            wrapper.set(Monitor::getResponseTimeAlertConfigIds, dto.getResponseTimeAlertConfigIds());
            monitorMapper.update(null, wrapper);
        }
        return monitorMapper.selectById(monitor.getId());
    }

    public Monitor updateMonitor(Long id, MonitorDTO dto) {
        Monitor monitor = monitorMapper.selectById(id);
        if (monitor == null) {
            return null;
        }
        BeanUtils.copyProperties(dto, monitor);
        monitor.setId(id);
        monitorMapper.updateById(monitor);

        LambdaUpdateWrapper<Monitor> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Monitor::getId, id);
        wrapper.set(Monitor::getResponseTimeAlertEnabled, dto.getResponseTimeAlertEnabled());
        wrapper.set(Monitor::getResponseTimeThreshold, dto.getResponseTimeThreshold());
        wrapper.set(Monitor::getResponseTimeConsecutiveCount, dto.getResponseTimeConsecutiveCount());
        wrapper.set(Monitor::getResponseTimeAlertConfigIds, dto.getResponseTimeAlertConfigIds());
        monitorMapper.update(null, wrapper);

        log.info("Updated monitor {}: responseTimeAlertEnabled={}, threshold={}, consecutive={}, configIds={}",
                id, dto.getResponseTimeAlertEnabled(), dto.getResponseTimeThreshold(),
                dto.getResponseTimeConsecutiveCount(), dto.getResponseTimeAlertConfigIds());
        return monitorMapper.selectById(id);
    }

    public boolean deleteMonitor(Long id) {
        return monitorMapper.deleteById(id) > 0;
    }

    public Monitor copyMonitor(Long id) {
        Monitor original = monitorMapper.selectById(id);
        if (original == null) return null;
        Monitor copy = new Monitor();
        BeanUtils.copyProperties(original, copy);
        copy.setId(null);
        copy.setName(original.getName() + " (副本)");
        copy.setCreatedAt(null);
        copy.setUpdatedAt(null);
        copy.setCreatedBy(null);
        monitorMapper.insert(copy);
        return monitorMapper.selectById(copy.getId());
    }

    public List<Monitor> getEnabledMonitors() {
        return monitorMapper.selectList(
                new LambdaQueryWrapper<Monitor>().eq(Monitor::getEnabled, true)
        );
    }

    public Long countEnabledMonitors() {
        return monitorMapper.selectCount(
                new LambdaQueryWrapper<Monitor>().eq(Monitor::getEnabled, true)
        );
    }

    public Long countDisabledMonitors() {
        return monitorMapper.selectCount(
                new LambdaQueryWrapper<Monitor>().eq(Monitor::getEnabled, false)
        );
    }

    public Map<Long, Long> getGroupCounts() {
        // 一次 GROUP BY 查询，替代加载全部 group_monitor 行
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Map<String, Object> row : groupMonitorMapper.countByMonitor()) {
            Object mid = row.get("mid");
            Object cnt = row.get("cnt");
            if (mid instanceof Number && cnt instanceof Number) {
                counts.put(((Number) mid).longValue(), ((Number) cnt).longValue());
            }
        }
        return counts;
    }

    public List<Map<String, Object>> getGroupsForMonitor(Long monitorId) {
        LambdaQueryWrapper<GroupMonitor> wrapper = new LambdaQueryWrapper<GroupMonitor>()
                .eq(GroupMonitor::getMonitorId, monitorId);
        List<GroupMonitor> gms = groupMonitorMapper.selectList(wrapper);
        if (gms.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // Batch load all groups in one query instead of N+1
        List<Long> groupIds = gms.stream().map(GroupMonitor::getGroupId).toList();
        Map<Long, MonitorGroup> groupMap = monitorGroupMapper.selectBatchIds(groupIds)
                .stream().collect(java.util.stream.Collectors.toMap(MonitorGroup::getId, g -> g));
        return gms.stream().map(gm -> {
            MonitorGroup group = groupMap.get(gm.getGroupId());
            Map<String, Object> map = new HashMap<>();
            map.put("groupId", gm.getGroupId());
            map.put("groupName", group != null ? group.getName() : "已删除任务");
            map.put("sortOrder", gm.getSortOrder());
            map.put("continueOnFail", gm.getContinueOnFail());
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void addMonitorToGroup(Long monitorId, Long groupId, Integer sortOrder, Boolean continueOnFail) {
        LambdaQueryWrapper<GroupMonitor> check = new LambdaQueryWrapper<GroupMonitor>()
                .eq(GroupMonitor::getMonitorId, monitorId)
                .eq(GroupMonitor::getGroupId, groupId);
        if (groupMonitorMapper.selectCount(check) > 0) {
            return;
        }
        GroupMonitor gm = new GroupMonitor();
        gm.setMonitorId(monitorId);
        gm.setGroupId(groupId);
        gm.setSortOrder(sortOrder != null ? sortOrder : 0);
        gm.setContinueOnFail(continueOnFail != null ? continueOnFail : true);
        gm.setVariableScope("GROUP");
        groupMonitorMapper.insert(gm);
    }

    @Transactional
    public void updateMonitorInGroup(Long monitorId, Long groupId, Integer sortOrder, Boolean continueOnFail) {
        LambdaUpdateWrapper<GroupMonitor> wrapper = new LambdaUpdateWrapper<GroupMonitor>()
                .eq(GroupMonitor::getMonitorId, monitorId)
                .eq(GroupMonitor::getGroupId, groupId);
        if (sortOrder != null) {
            wrapper.set(GroupMonitor::getSortOrder, sortOrder);
        }
        if (continueOnFail != null) {
            wrapper.set(GroupMonitor::getContinueOnFail, continueOnFail);
        }
        groupMonitorMapper.update(null, wrapper);
    }

    @Transactional
    public void removeMonitorFromGroup(Long monitorId, Long groupId) {
        LambdaQueryWrapper<GroupMonitor> wrapper = new LambdaQueryWrapper<GroupMonitor>()
                .eq(GroupMonitor::getMonitorId, monitorId)
                .eq(GroupMonitor::getGroupId, groupId);
        groupMonitorMapper.delete(wrapper);
    }

    @Transactional
    public void batchUpdateStatus(List<Long> ids, Boolean enabled) {
        LambdaUpdateWrapper<Monitor> wrapper = new LambdaUpdateWrapper<Monitor>()
                .in(Monitor::getId, ids)
                .set(Monitor::getEnabled, enabled);
        monitorMapper.update(null, wrapper);
    }
}
