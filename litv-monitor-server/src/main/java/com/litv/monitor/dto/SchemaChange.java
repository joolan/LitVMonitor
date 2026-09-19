package com.litv.monitor.dto;

import lombok.Data;

@Data
public class SchemaChange {
    private String changeType;
    private String fieldPath;
    private String description;
    private String oldType;
    private String newType;

    public SchemaChange(String changeType, String fieldPath, String description) {
        this.changeType = changeType;
        this.fieldPath = fieldPath;
        this.description = description;
    }

    public SchemaChange(String changeType, String fieldPath, String description, String oldType, String newType) {
        this.changeType = changeType;
        this.fieldPath = fieldPath;
        this.description = description;
        this.oldType = oldType;
        this.newType = newType;
    }
}
