package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class MysqlMonitorExecutor extends AbstractMonitorExecutor {

    @Override
    public String getMonitorType() {
        return "MYSQL";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = getConfigValue(monitor, "host");
        int port = getIntConfigValue(monitor, "port", 3306);

        if (host == null || host.isEmpty()) {
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage("未配置主机地址");
            return;
        }

        int timeout = monitor.getTimeout() != null ? monitor.getTimeout() * 1000 : 5000;
        String target = host + ":" + port;
        logEntry.setUrl("mysql:" + target);
        logEntry.setDomain(host);

        try {
            InetAddress addr = InetAddress.getByName(host);
            logEntry.setIpAddress(addr.getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                InputStream is = socket.getInputStream();

                // Read MySQL Initial Handshake Packet
                // Packet format: 3 bytes length + 1 byte sequence id + payload
                byte[] header = readBytes(is, 4);
                if (header == null) {
                    logEntry.setStatus("FAIL");
                    logEntry.setErrorMessage("未收到MySQL握手包");
                    logEntry.setStatusCode(0);
                    return;
                }

                int packetLen = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8) | ((header[2] & 0xFF) << 16);
                byte seqId = header[3];

                byte[] payload = readBytes(is, packetLen);
                long rtt = System.currentTimeMillis() - start;

                logEntry.setResponseTime((int) rtt);

                if (payload == null || payload.length < 5) {
                    logEntry.setStatus("FAIL");
                    logEntry.setErrorMessage("MySQL握手包格式异常");
                    logEntry.setStatusCode(0);
                    return;
                }

                // First byte is protocol version (0x0a = 10 for MySQL 4.1+)
                int protocolVersion = payload[0] & 0xFF;
                if (protocolVersion != 0x0a) {
                    logEntry.setStatus("FAIL");
                    logEntry.setErrorMessage("非MySQL协议响应 (protocol=" + protocolVersion + ")");
                    logEntry.setStatusCode(0);
                    return;
                }

                // Read server version string (null-terminated)
                int endIdx = 1;
                while (endIdx < payload.length && payload[endIdx] != 0) {
                    endIdx++;
                }
                String serverVersion = new String(payload, 1, endIdx - 1, StandardCharsets.US_ASCII);

                logEntry.setResponseBody(serverVersion);
                logEntry.setStatusCode(protocolVersion);
                logEntry.setStatus("SUCCESS");
            }
        } catch (Exception e) {
            logEntry.setResponseTime(0);
            logEntry.setStatus("FAIL");
            logEntry.setStatusCode(0);
            logEntry.setErrorMessage("MySQL连接失败: " + e.getMessage());
            log.debug("MySQL connection failed for {}: {}", target, e.getMessage());
        }
    }
}
