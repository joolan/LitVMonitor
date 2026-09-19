package com.litv.monitor.enums;

import lombok.Getter;

@Getter
public enum ReminderCategory {
    SERVER_RENEWAL("服务器续费", 30),
    SSL_RENEWAL("SSL证书续费", 30),
    UTILITY("机房费用", 7),
    ON_SITE_INSPECTION("定期巡检", 3),
    TASK_REMINDER("事务提醒", 1),
    OTHER("其他", 1);

    private final String label;
    private final int defaultAdvanceDays;

    ReminderCategory(String label, int defaultAdvanceDays) {
        this.label = label;
        this.defaultAdvanceDays = defaultAdvanceDays;
    }
}
