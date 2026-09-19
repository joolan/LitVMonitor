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
public class AmqpMonitorExecutor extends AbstractMonitorExecutor {

    // AMQP 0-9-1 protocol header: "AMQP" + 0x00 0x00 0x09 0x01
    private static final byte[] PROTOCOL_HEADER = new byte[]{'A', 'M', 'Q', 'P', 0x00, 0x00, 0x09, 0x01};

    @Override
    public String getMonitorType() {
        return "AMQP";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 5672);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("amqp://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                os.write(PROTOCOL_HEADER);
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] buf = readBytes(is, 8);
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                if (buf == null || buf.length == 0) {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("AMQP服务无响应");
                    return;
                }

                String preview = new String(buf, 0, buf.length, StandardCharsets.US_ASCII);
                logEntry.setResponseBody(preview.replaceAll("[^\\x20-\\x7E]", "."));

                // Either the server echoes the protocol header ("AMQP") or sends
                // a connection.start frame (type 0x01) for a supported version
                boolean isProtocolHeader = buf.length >= 4
                        && buf[0] == 'A' && buf[1] == 'M' && buf[2] == 'Q' && buf[3] == 'P';
                boolean isConnectionStart = buf[0] == 0x01;

                if (isProtocolHeader || isConnectionStart) {
                    logEntry.setStatus("SUCCESS");
                    logEntry.setStatusCode(1);
                } else {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非AMQP服务响应");
                }
            }
        } catch (Exception e) {
            markFail(logEntry, "AMQP连接失败: " + e.getMessage());
            log.debug("AMQP connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
