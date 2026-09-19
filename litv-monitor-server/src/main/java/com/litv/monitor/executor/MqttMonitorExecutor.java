package com.litv.monitor.executor;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;

@Slf4j
@Component
public class MqttMonitorExecutor extends AbstractMonitorExecutor {

    // MQTT 3.1.1 CONNECT with clean session, no credentials, client id "litv-mon"
    private static final byte[] CONNECT_PACKET = new byte[]{
            0x10, 0x14,                                     // CONNECT, remaining length 20
            0x00, 0x04, 'M', 'Q', 'T', 'T',                 // protocol name "MQTT"
            0x04,                                           // protocol level 4 (3.1.1)
            0x02,                                           // connect flags: clean session
            0x00, 0x3C,                                     // keep alive 60s
            0x00, 0x08, 'l', 'i', 't', 'v', '-', 'm', 'o', 'n' // client id
    };

    @Override
    public String getMonitorType() {
        return "MQTT";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 1883);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("mqtt://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                os.write(CONNECT_PACKET);
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buf = readBytes(is, 4);
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                if (buf == null || buf.length < 2 || (buf[0] & 0xF0) != 0x20) {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非MQTT服务响应");
                    return;
                }

                int returnCode = buf.length >= 4 ? (buf[3] & 0xFF) : -1;
                String desc = switch (returnCode) {
                    case 0 -> "accepted";
                    case 1 -> "unacceptable protocol version";
                    case 2 -> "identifier rejected";
                    case 3 -> "server unavailable";
                    case 4 -> "bad username or password";
                    case 5 -> "not authorized";
                    default -> "unknown";
                };
                logEntry.setResponseBody("CONNACK returnCode=" + returnCode + " (" + desc + ")");
                logEntry.setStatusCode(returnCode);
                logEntry.setStatus("SUCCESS");
            }
        } catch (Exception e) {
            markFail(logEntry, "MQTT连接失败: " + e.getMessage());
            log.debug("MQTT connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
