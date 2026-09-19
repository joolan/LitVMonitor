package com.litv.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litv.monitor.entity.AlertRateLimit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AlertRateLimitMapper extends BaseMapper<AlertRateLimit> {

    /** 原子自增失败计数（避免读-改-写导致丢更新）。 */
    @Update("UPDATE alert_rate_limit SET current_count = current_count + 1, " +
            "window_start = COALESCE(window_start, #{now}), " +
            "last_alert_success = 0, consecutive_success_count = 0, updated_at = #{now} " +
            "WHERE fingerprint = #{fingerprint}")
    int incrementFailure(@Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

    /** 原子自增连续成功计数。 */
    @Update("UPDATE alert_rate_limit SET consecutive_success_count = COALESCE(consecutive_success_count, 0) + 1, " +
            "last_alert_success = 1, updated_at = #{now} " +
            "WHERE fingerprint = #{fingerprint}")
    int incrementSuccess(@Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

    /** 成功时重置窗口计数。 */
    @Update("UPDATE alert_rate_limit SET current_count = 0, window_start = NULL, " +
            "last_alert_success = 1, updated_at = #{now} " +
            "WHERE fingerprint = #{fingerprint}")
    int resetOnSuccess(@Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

    /** 完全重置（恢复通知发送后）。 */
    @Update("UPDATE alert_rate_limit SET current_count = 0, window_start = NULL, " +
            "last_alert_success = NULL, consecutive_success_count = 0, updated_at = #{now} " +
            "WHERE fingerprint = #{fingerprint}")
    int resetAll(@Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);
}
