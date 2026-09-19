package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;

@Slf4j
@Component
public class PingMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "PING";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = getConfigValue(monitor, "host");
        if (host == null || host.isEmpty()) {
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage("未配置主机地址");
            return;
        }

        logEntry.setUrl("ping:" + host);
        logEntry.setDomain(host);

        try {
            InetAddress addr = InetAddress.getByName(host);
            if (ssrfGuard != null) ssrfGuard.assertAllowed(addr);
            logEntry.setIpAddress(addr.getHostAddress());

            long start = System.currentTimeMillis();
            boolean reachable = addr.isReachable(5000);
            long rtt = System.currentTimeMillis() - start;

            logEntry.setResponseTime((int) rtt);
            logEntry.setStatusCode(reachable ? 1 : 0);
            logEntry.setResponseBody(reachable ? "Reply from " + host + ": bytes=32 time=" + rtt + "ms TTL=128" : null);

            if (reachable) {
                logEntry.setStatus("SUCCESS");
            } else {
                logEntry.setStatus("FAIL");
                logEntry.setErrorMessage("主机不可达 (ping timeout)");
            }
        } catch (Exception e) {
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage("Ping执行异常: " + e.getMessage());
            log.error("Ping execution failed for host: {}", host, e);
        }
    }
}
