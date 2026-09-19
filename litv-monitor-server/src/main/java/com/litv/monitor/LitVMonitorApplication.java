package com.litv.monitor;

import com.litv.monitor.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
public class LitVMonitorApplication implements CommandLineRunner {

    private final UserService userService;

    public static void main(String[] args) {
        // 统一时区为北京时间，避免不同主机时区导致 LocalDateTime.now() 不一致
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(LitVMonitorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("Initializing default admin user...");
        String generatedPassword = userService.initAdminUser();
        if (generatedPassword != null) {
            log.warn("============================================================");
            log.warn(" 首次启动已创建管理员账号: admin");
            log.warn(" 初始随机密码: {}", generatedPassword);
            log.warn(" 请登录后立即修改密码（系统会强制要求修改）");
            log.warn(" 也可通过环境变量 ADMIN_INIT_PASSWORD 预设初始密码");
            log.warn("============================================================");
        }
    }
}
