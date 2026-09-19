package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class FtpMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "FTP";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 21);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("ftp://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String banner = reader.readLine();
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));
                logEntry.setResponseBody(banner);

                if (banner != null && banner.startsWith("220")) {
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(220);
                } else {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非FTP服务响应: " + (banner != null ? banner : "空"));
                }
            }
        } catch (Exception e) {
            markFail(logEntry, "FTP连接失败: " + e.getMessage());
            log.debug("FTP connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
