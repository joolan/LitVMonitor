package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

@Slf4j
@Component
public class TcpMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "TCP";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = getConfigValue(monitor, "host");
        int port = getIntConfigValue(monitor, "port", 80);

        if (host == null || host.isEmpty()) {
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage("未配置主机地址");
            return;
        }

        int timeout = monitor.getTimeout() != null ? monitor.getTimeout() * 1000 : 5000;
        String target = host + ":" + port;
        logEntry.setUrl("tcp:" + target);
        logEntry.setDomain(host);

        try {
            InetAddress addr = InetAddress.getByName(host);
            logEntry.setIpAddress(addr.getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                long rtt = System.currentTimeMillis() - start;

                logEntry.setResponseTime((int) rtt);
                logEntry.setStatusCode(1);
                logEntry.setResponseBody("TCP port " + port + " is open");
                logEntry.setStatus("SUCCESS");
            }
        } catch (Exception e) {
            long rtt = System.currentTimeMillis() - System.currentTimeMillis();
            logEntry.setResponseTime(0);
            logEntry.setStatusCode(0);
            logEntry.setStatus("FAIL");
            logEntry.setErrorMessage("TCP连接失败: " + e.getMessage());
            log.debug("TCP connection failed for {}: {}", target, e.getMessage());
        }
    }
}
