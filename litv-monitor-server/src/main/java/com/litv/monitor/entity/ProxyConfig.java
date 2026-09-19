package com.litv.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("proxy_config")
public class ProxyConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String proxyType;

    private String host;

    private Integer port;

    private String username;

    /** 仅用于写入（请求体），响应中不返回，避免密码泄露。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private Boolean enabled;

    private Boolean active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 响应字段：是否已配置密码（不回传明文）。 */
    public boolean isHasPassword() {
        return password != null && !password.isEmpty();
    }
}
