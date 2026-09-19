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
public class PostgresMonitorExecutor extends AbstractMonitorExecutor {

    // PostgreSQL SSLRequest: int32 length(8) + int32 code(80877103)
    private static final byte[] SSL_REQUEST = new byte[]{0, 0, 0, 8, 0x04, (byte) 0xD2, 0x16, 0x2F};

    @Override
    public String getMonitorType() {
        return "POSTGRESQL";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 5432);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("postgresql://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                os.write(SSL_REQUEST);
                os.flush();

                InputStream is = socket.getInputStream();
                int resp = is.read();
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                if (resp == 'S') {
                    logEntry.setResponseBody("SSL supported");
                    logEntry.setStatusCode(1);
                    logEntry.setStatus("SUCCESS");
                } else if (resp == 'N') {
                    logEntry.setResponseBody("SSL not supported");
                    logEntry.setStatusCode(1);
                    logEntry.setStatus("SUCCESS");
                } else {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非PostgreSQL服务响应: " + (resp < 0 ? "空" : (char) resp));
                }
            }
        } catch (Exception e) {
            markFail(logEntry, "PostgreSQL连接失败: " + e.getMessage());
            log.debug("PostgreSQL connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }
}
