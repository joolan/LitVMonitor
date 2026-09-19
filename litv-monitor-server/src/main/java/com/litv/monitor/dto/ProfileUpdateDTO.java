package com.litv.monitor.dto;

import lombok.Data;

@Data
public class ProfileUpdateDTO {
    private String oldPassword;
    private String newPassword;
    private String newPasswordConfirm;
    private String nickname;
    private String email;
}
