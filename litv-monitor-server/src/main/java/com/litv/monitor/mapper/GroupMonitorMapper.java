package com.litv.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litv.monitor.entity.GroupMonitor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMonitorMapper extends BaseMapper<GroupMonitor> {

    /** 每个监控项关联的任务数（一次查询，避免逐行 N+1）。 */
    @Select("SELECT monitor_id AS mid, COUNT(*) AS cnt FROM group_monitor GROUP BY monitor_id")
    List<Map<String, Object>> countByMonitor();

    /** 每个任务包含的监控项数（一次查询，避免逐行 N+1）。 */
    @Select("SELECT group_id AS gid, COUNT(*) AS cnt FROM group_monitor GROUP BY group_id")
    List<Map<String, Object>> countByGroup();
}
