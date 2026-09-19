package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class TelnetMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "TELNET";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 23);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("telnet://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                InputStream is = socket.getInputStream();
                byte[] buf = new byte[1024];
                int bytesRead;
                try {
                    bytesRead = is.read(buf);
                } catch (SocketTimeoutException ste) {
                    // Some telnet services wait for client input before sending a banner
                    OutputStream os = socket.getOutputStream();
                    os.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    os.flush();
                    bytesRead = is.read(buf);
                }

                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                if (bytesRead <= 0) {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("Telnet服务无响应");
                    return;
                }

                logEntry.setResponseBody(toPrintable(buf, bytesRead));
                logEntry.setStatus("SUCCESS");
                logEntry.setStatusCode(1);
            }
        } catch (Exception e) {
            markFail(logEntry, "Telnet连接失败: " + e.getMessage());
            log.debug("Telnet connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }

    private String toPrintable(byte[] buf, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int b = buf[i] & 0xFF;
            if (b == '\r' || b == '\n' || b == '\t' || (b >= 32 && b < 127)) {
                sb.append((char) b);
            } else {
                sb.append('.');
            }
        }
        return sb.toString().trim();
    }
}
