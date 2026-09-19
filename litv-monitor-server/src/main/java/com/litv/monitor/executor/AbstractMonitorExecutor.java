package com.litv.monitor.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.service.SsrfProtectionService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

public abstract class AbstractMonitorExecutor implements MonitorExecutor {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    protected SsrfProtectionService ssrfGuard;

    protected String getConfigValue(Monitor monitor, String key) {
        if (monitor.getConfig() == null || monitor.getConfig().isEmpty()) return null;
        try {
            JsonNode node = MAPPER.readTree(monitor.getConfig());
            return node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    protected int getIntConfigValue(Monitor monitor, String key, int defaultVal) {
        String val = getConfigValue(monitor, key);
        if (val == null || val.isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    protected String resolveHost(Monitor monitor) {
        String host = getConfigValue(monitor, "host");
        if (host != null && !host.isEmpty()) return host;
        return parseHostFromUrl(monitor.getUrl());
    }

    protected int resolvePort(Monitor monitor, int defaultPort) {
        return getIntConfigValue(monitor, "port", defaultPort);
    }

    protected int timeoutMillis(Monitor monitor) {
        return monitor.getTimeout() != null ? monitor.getTimeout() * 1000 : 5000;
    }

    protected Socket openSocket(String host, int port, int timeout) throws IOException {
        // Resolve + SSRF check, then connect to the validated address (avoids DNS rebinding TOCTOU)
        InetAddress addr = (ssrfGuard != null)
                ? ssrfGuard.resolveAndCheck(host)
                : InetAddress.getByName(host);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(addr, port), timeout);
        socket.setSoTimeout(timeout);
        return socket;
    }

    protected static byte[] readBytes(InputStream is, int count) throws IOException {
        byte[] buf = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = is.read(buf, offset, count - offset);
            if (read < 0) return offset > 0 ? Arrays.copyOf(buf, offset) : null;
            offset += read;
        }
        return buf;
    }

    protected void markFail(ExecutionLog logEntry, String message) {
        logEntry.setResponseTime(0);
        logEntry.setStatusCode(0);
        logEntry.setStatus("FAIL");
        logEntry.setErrorMessage(message);
    }

    protected void markError(ExecutionLog logEntry, String message) {
        logEntry.setStatus("ERROR");
        logEntry.setErrorMessage(message);
    }

    protected String parseHostFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        String s = url;
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int colon = s.lastIndexOf(':');
        if (colon > 0) s = s.substring(0, colon);
        return s.isEmpty() ? null : s;
    }
}
