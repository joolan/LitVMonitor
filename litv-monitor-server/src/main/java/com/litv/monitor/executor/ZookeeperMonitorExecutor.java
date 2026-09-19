package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class ZookeeperMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "ZOOKEEPER";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 2181);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("zookeeper://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                os.write("ruok".getBytes(StandardCharsets.US_ASCII));
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buf = new byte[16];
                int bytesRead = is.read(buf);
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                String response = bytesRead > 0
                        ? new String(buf, 0, bytesRead, StandardCharsets.US_ASCII).trim() : "";

                if ("imok".equals(response)) {
                    logEntry.setResponseBody("imok");
                    logEntry.setStatusCode(1);
                    logEntry.setStatus("SUCCESS");
                } else {
                    // TCP connection succeeded so the service is alive, but the ruok
                    // four-letter-word may be disabled by the 4lw.commands.whitelist
                    logEntry.setResponseBody(response.isEmpty()
                            ? "已连接，但ruok未响应（可能未启用4lw白名单）" : response);
                    logEntry.setStatusCode(1);
                    logEntry.setStatus("SUCCESS");
                }
            }
        } catch (Exception e) {
            markFail(logEntry, "ZooKeeper连接失败: " + e.getMessage());
            log.debug("ZooKeeper connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
