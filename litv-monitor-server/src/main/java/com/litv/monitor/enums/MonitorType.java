package com.litv.monitor.enums;

import lombok.Getter;

@Getter
public enum MonitorType {

    HTTP("HTTP", "HTTP请求监控"),
    PING("PING", "Ping可达性检测"),
    TCP("TCP", "TCP端口连通检测"),
    SSH("SSH", "SSH服务探测"),
    TELNET("TELNET", "Telnet服务探测"),
    FTP("FTP", "FTP服务探测"),
    VNC("VNC", "VNC服务探测"),
    MYSQL("MYSQL", "MySQL服务探测"),
    POSTGRESQL("POSTGRESQL", "PostgreSQL服务探测"),
    REDIS("REDIS", "Redis服务探测"),
    MEMCACHED("MEMCACHED", "Memcached服务探测"),
    MONGODB("MONGODB", "MongoDB服务探测"),
    ZOOKEEPER("ZOOKEEPER", "ZooKeeper服务探测"),
    AMQP("AMQP", "AMQP/RabbitMQ服务探测"),
    MQTT("MQTT", "MQTT服务探测");

    private final String code;
    private final String description;

    MonitorType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MonitorType fromCode(String code) {
        for (MonitorType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return HTTP;
    }
}
