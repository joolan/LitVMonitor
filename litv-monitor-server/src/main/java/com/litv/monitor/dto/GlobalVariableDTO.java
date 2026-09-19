package com.litv.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GlobalVariableDTO {

    private Long id;

    @NotBlank(message = "变量名不能为空")
    private String name;

    private String value;

    private String description;

    private Boolean isSecret;
}
