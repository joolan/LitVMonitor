package com.litv.monitor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDTO {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    private String nickname;

    private String email;

    @NotBlank(message = "角色不能为空")
    private String role;

    private Boolean enabled;
}
