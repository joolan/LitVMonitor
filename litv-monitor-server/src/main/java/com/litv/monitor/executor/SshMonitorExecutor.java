package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

@Slf4j
@Component
public class SshMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "SSH";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = getConfigValue(monitor, "host");
        int port = getIntConfigValue(monitor, "port", 22);

        if (host == null || host.isEmpty()) {
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage("未配置主机地址");
            return;
        }

        int timeout = monitor.getTimeout() != null ? monitor.getTimeout() * 1000 : 5000;
        String target = host + ":" + port;
        logEntry.setUrl("ssh:" + target);
        logEntry.setDomain(host);

        try {
            InetAddress addr = InetAddress.getByName(host);
            logEntry.setIpAddress(addr.getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String banner = reader.readLine();
                long rtt = System.currentTimeMillis() - start;

                logEntry.setResponseTime((int) rtt);
                logEntry.setResponseBody(banner);

                if (banner != null && banner.startsWith("SSH-")) {
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(22);
                } else {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非SSH服务响应: " + (banner != null ? banner : "空"));
                }
            }
        } catch (Exception e) {
            logEntry.setResponseTime(0);
            logEntry.setStatus("FAIL");
            logEntry.setStatusCode(0);
            logEntry.setErrorMessage("SSH连接失败: " + e.getMessage());
            log.debug("SSH connection failed for {}: {}", target, e.getMessage());
        }
    }
}
