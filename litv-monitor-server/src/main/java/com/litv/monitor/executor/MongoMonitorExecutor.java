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
public class MongoMonitorExecutor extends AbstractMonitorExecutor {

    private static final int OP_MSG = 2013;
    private static final byte[] HELLO_COMMAND = buildHelloCommand();

    @Override
    public String getMonitorType() {
        return "MONGODB";
    }

    @Override
    public void execute(Monitor monitor, ExecutionLog logEntry, Map<String, String> vars) {
        String host = resolveHost(monitor);
        int port = resolvePort(monitor, 27017);

        if (host == null || host.isEmpty()) {
            markError(logEntry, "未配置主机地址");
            return;
        }

        int timeout = timeoutMillis(monitor);
        logEntry.setUrl("mongodb://" + host + ":" + port);
        logEntry.setDomain(host);

        try {
            logEntry.setIpAddress(InetAddress.getByName(host).getHostAddress());

            long start = System.currentTimeMillis();
            try (Socket socket = openSocket(host, port, timeout)) {
                OutputStream os = socket.getOutputStream();
                os.write(HELLO_COMMAND);
                os.flush();

                InputStream is = socket.getInputStream();
                byte[] lenBuf = readBytes(is, 4);
                if (lenBuf == null) {
                    logEntry.setResponseTime((int) (System.currentTimeMillis() - start));
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("MongoDB无响应");
                    return;
                }

                int messageLength = readInt32(lenBuf, 0);
                if (messageLength < 16 || messageLength > 1024 * 1024) {
                    logEntry.setResponseTime((int) (System.currentTimeMillis() - start));
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("MongoDB响应长度异常: " + messageLength);
                    return;
                }

                byte[] body = readBytes(is, messageLength - 4);
                logEntry.setResponseTime((int) (System.currentTimeMillis() - start));

                byte[] full = new byte[messageLength];
                System.arraycopy(lenBuf, 0, full, 0, 4);
                if (body != null) {
                    System.arraycopy(body, 0, full, 4, body.length);
                }

                int opCode = readInt32(full, 12);
                if (opCode != OP_MSG) {
                    logEntry.setStatus("FAIL");
                    logEntry.setStatusCode(0);
                    logEntry.setErrorMessage("非MongoDB协议响应 (opCode=" + opCode + ")");
                    return;
                }

                Integer maxWireVersion = extractMaxWireVersion(full);
                logEntry.setResponseBody(maxWireVersion != null
                        ? "hello OK, maxWireVersion=" + maxWireVersion
                        : "hello OK");
                logEntry.setStatusCode(1);
                logEntry.setStatus("SUCCESS");
            }
        } catch (Exception e) {
            markFail(logEntry, "MongoDB连接失败: " + e.getMessage());
            log.debug("MongoDB connection failed for {}:{}: {}", host, port, e.getMessage());
        }
    }

    private static int readInt32(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static Integer extractMaxWireVersion(byte[] buf) {
        byte[] key = "maxWireVersion".getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 16; i <= buf.length - key.length - 5; i++) {
            for (int j = 0; j < key.length; j++) {
                if (buf[i + j] != key[j]) continue outer;
            }
            // element: type(1) + name + 0x00 + int32 value
            int valueOffset = i + key.length + 1;
            if (valueOffset + 4 <= buf.length) {
                return readInt32(buf, valueOffset);
            }
        }
        return null;
    }

    private static byte[] buildHelloCommand() {
        // BSON document { hello: 1, $db: "admin" }
        byte[] doc = new byte[]{
                0x1F, 0x00, 0x00, 0x00,                         // document size = 31
                0x10, 'h', 'e', 'l', 'l', 'o', 0x00,            // int32 "hello"
                0x01, 0x00, 0x00, 0x00,                         // value = 1
                0x02, '$', 'd', 'b', 0x00,                      // string "$db"
                0x06, 0x00, 0x00, 0x00,                         // string length = 6
                'a', 'd', 'm', 'i', 'n', 0x00,                  // "admin"
                0x00                                            // document terminator
        };
        int messageLength = 16 + 4 + 1 + doc.length;
        byte[] msg = new byte[messageLength];
        msg[0] = (byte) messageLength;                          // messageLength (LE)
        msg[4] = 0x01;                                          // requestID = 1
        msg[12] = (byte) 0xDD;                                  // opCode = 2013 (0x07DD)
        msg[13] = 0x07;
        System.arraycopy(doc, 0, msg, 21, doc.length);          // section kind 0 + BSON doc
        return msg;
    }
}
