package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.MonitorGroupDTO;
import com.litv.monitor.dto.MonitorGroupItemDTO;
import com.litv.monitor.entity.GroupMonitor;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.entity.MonitorGroup;
import com.litv.monitor.mapper.GroupMonitorMapper;
import com.litv.monitor.mapper.MonitorGroupMapper;
import com.litv.monitor.mapper.MonitorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorGroupService {

    private final MonitorGroupMapper monitorGroupMapper;
    private final GroupMonitorMapper groupMonitorMapper;
    private final MonitorMapper monitorMapper;

    public Page<MonitorGroup> listGroups(Page<MonitorGroup> page, String keyword, Boolean enabled, Long id) {
        LambdaQueryWrapper<MonitorGroup> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            wrapper.eq(MonitorGroup::getId, id);
        } else if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MonitorGroup::getName, keyword);
        }
        if (enabled != null) {
            wrapper.eq(MonitorGroup::getEnabled, enabled);
        }
        wrapper.orderByDesc(MonitorGroup::getCreatedAt);
        Page<MonitorGroup> result = monitorGroupMapper.selectPage(page, wrapper);

        // Populate monitorCount for each group (one GROUP BY query, no N+1)
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : groupMonitorMapper.countByGroup()) {
            Object gid = row.get("gid");
            Object cnt = row.get("cnt");
            if (gid instanceof Number && cnt instanceof Number) {
                counts.put(((Number) gid).longValue(), ((Number) cnt).longValue());
            }
        }
        for (MonitorGroup group : result.getRecords()) {
            group.setMonitorCount(counts.getOrDefault(group.getId(), 0L).intValue());
        }

        return result;
    }

    public MonitorGroup getGroupById(Long id) {
        return monitorGroupMapper.selectById(id);
    }

    @Transactional
    public MonitorGroup createGroup(MonitorGroupDTO dto) {
        MonitorGroup group = new MonitorGroup();
        BeanUtils.copyProperties(dto, group);
        monitorGroupMapper.insert(group);

        // Save monitors
        if (dto.getMonitors() != null) {
            saveGroupMonitors(group.getId(), dto.getMonitors());
        }

        return group;
    }

    @Transactional
    public MonitorGroup updateGroup(Long id, MonitorGroupDTO dto) {
        MonitorGroup group = monitorGroupMapper.selectById(id);
        if (group == null) {
            return null;
        }

        BeanUtils.copyProperties(dto, group);
        group.setId(id);
        monitorGroupMapper.updateById(group);

        // Delete existing monitors and save new ones
        LambdaQueryWrapper<GroupMonitor> deleteWrapper = new LambdaQueryWrapper<GroupMonitor>()
                .eq(GroupMonitor::getGroupId, id);
        groupMonitorMapper.delete(deleteWrapper);

        if (dto.getMonitors() != null) {
            saveGroupMonitors(id, dto.getMonitors());
        }

        return group;
    }

    @Transactional
    public boolean deleteGroup(Long id) {
        // Delete associated monitors first
        LambdaQueryWrapper<GroupMonitor> deleteWrapper = new LambdaQueryWrapper<GroupMonitor>()
                .eq(GroupMonitor::getGroupId, id);
        groupMonitorMapper.delete(deleteWrapper);

        return monitorGroupMapper.deleteById(id) > 0;
    }

    private void saveGroupMonitors(Long groupId, List<MonitorGroupItemDTO> monitors) {
        for (int i = 0; i < monitors.size(); i++) {
            MonitorGroupItemDTO item = monitors.get(i);
            GroupMonitor gm = new GroupMonitor();
            gm.setGroupId(groupId);
            gm.setMonitorId(item.getMonitorId());
            gm.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : i);
            gm.setContinueOnFail(item.getContinueOnFail() != null ? item.getContinueOnFail() : true);
            gm.setIsGroupStart(item.getIsGroupStart() != null ? item.getIsGroupStart() : false);
            gm.setVariableScope(item.getVariableScope() != null ? item.getVariableScope() : "GROUP");
            groupMonitorMapper.insert(gm);
        }
    }

    public List<GroupMonitor> getGroupMonitors(Long groupId) {
        List<GroupMonitor> list = groupMonitorMapper.selectList(
                new LambdaQueryWrapper<GroupMonitor>()
                        .eq(GroupMonitor::getGroupId, groupId)
                        .orderByAsc(GroupMonitor::getSortOrder)
        );
        // Populate monitor names
        for (GroupMonitor gm : list) {
            Monitor monitor = monitorMapper.selectById(gm.getMonitorId());
            if (monitor != null) {
                gm.setMonitorName(monitor.getName());
            }
        }
        return list;
    }

    public Long countGroups() {
        return monitorGroupMapper.selectCount(null);
    }

    public Long countEnabledGroups() {
        return monitorGroupMapper.selectCount(
                new LambdaQueryWrapper<MonitorGroup>().eq(MonitorGroup::getEnabled, true)
        );
    }

    public Long countDisabledGroups() {
        return monitorGroupMapper.selectCount(
                new LambdaQueryWrapper<MonitorGroup>().eq(MonitorGroup::getEnabled, false)
        );
    }
}
