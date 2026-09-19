package com.litv.monitor.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    public static String format(LocalDateTime dt) {
        if (dt == null) return null;
        return dt.format(FORMATTER);
    }
}
