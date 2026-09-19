package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class MemcachedMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "MEMCACHED";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 11211);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("memcached://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                os.write("version\r\n".getBytes(StandardCharsets.US_ASCII));
                os.flush();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String line = reader.readLine();
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));
                logEntry.setResponseBody(line);

                if (line != null && line.startsWith("VERSION")) {
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(1);
                } else {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非Memcached服务响应: " + (line != null ? line : "空"));
                }
            }
        } catch (Exception e) {
            markFail(logEntry, "Memcached连接失败: " + e.getMessage());
            log.debug("Memcached connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
