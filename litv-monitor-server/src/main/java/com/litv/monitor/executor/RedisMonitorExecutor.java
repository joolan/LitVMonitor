package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class RedisMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "REDIS";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = getConfigValue(monitor, "host");
        int port = getIntConfigValue(monitor, "port", 6379);

        if (host == null || host.isEmpty()) {
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage("未配置主机地址");
            return;
        }

        int timeout = monitor.getTimeout() != null ? monitor.getTimeout() * 1000 : 5000;
        String target = host + ":" + port;
        logEntry.setUrl("redis:" + target);
        logEntry.setDomain(host);

        try {
            InetAddress addr = InetAddress.getByName(host);
            logEntry.setIpAddress(addr.getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();

                // Send PING command in RESP protocol
                String pingCmd = "*1\r\n$4\r\nPING\r\n";
                os.write(pingCmd.getBytes(StandardCharsets.US_ASCII));
                os.flush();

                // Read response
                byte[] buf = new byte[1024];
                int bytesRead = is.read(buf);
                long rtt = System.currentTimeMillis() - start;

                logEntry.setResponseTime((int) rtt);

                if (bytesRead <= 0) {
                    logEntry.setStatus("FAIL");
                    logEntry.setErrorMessage("Redis未返回响应");
                    logEntry.setStatusCode(0);
                    return;
                }

                String response = new String(buf, 0, bytesRead, StandardCharsets.US_ASCII).trim();
                logEntry.setResponseBody(response);

                // +PONG is the expected response for PING
                if ("+PONG".equals(response)) {
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(1);
                } else if (response.startsWith("-")) {
                    // Redis error response
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("Redis错误: " + response);
                } else {
                    // Got some response, treat as success (server is alive)
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(1);
                }
            }
        } catch (Exception e) {
            logEntry.setResponseTime(0);
            logEntry.setStatus("FAIL");
            logEntry.setStatusCode(0);
            logEntry.setErrorMessage("Redis连接失败: " + e.getMessage());
            log.debug("Redis connection failed for {}: {}", target, e.getMessage());
        }
    }
}
