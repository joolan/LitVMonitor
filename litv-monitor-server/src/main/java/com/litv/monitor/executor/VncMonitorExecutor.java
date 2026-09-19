package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class VncMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "VNC";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 5900);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("vnc://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                InputStream is = socket.getInputStream();
                byte[] buf = readBytes(is, 12);
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                if (buf == null || buf.length < 4) {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("未收到VNC协议版本");
                    return;
                }

                String version = new String(buf, 0, buf.length, StandardCharsets.US_ASCII).trim();
                logEntry.setResponseBody(version);

                if (version.startsWith("RFB")) {
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(1);
                } else {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非VNC服务响应: " + version);
                }
            }
        } catch (Exception e) {
            markFail(logEntry, "VNC连接失败: " + e.getMessage());
            log.debug("VNC connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
