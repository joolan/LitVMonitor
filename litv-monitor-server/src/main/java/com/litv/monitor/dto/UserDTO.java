package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
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
