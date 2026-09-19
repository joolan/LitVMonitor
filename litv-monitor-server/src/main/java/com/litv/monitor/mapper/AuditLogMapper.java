package com.litv.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litv.monitor.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
