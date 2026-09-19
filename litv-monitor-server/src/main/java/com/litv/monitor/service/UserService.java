package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.UserDTO;
import com.litv.monitor.entity.SysUser;
import com.litv.monitor.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INIT_PASSWORD:}")
    private String adminInitPassword;

    public SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
        );
    }

    public SysUser findById(Long id) {
        return sysUserMapper.selectById(id);
    }

    public Page<SysUser> listUsers(Page<SysUser> page) {
        return sysUserMapper.selectPage(page, new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt));
    }

    public SysUser createUser(UserDTO dto) {
        validatePasswordPolicy(dto.getPassword());
        validateRole(dto.getRole());
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        user.setMustChangePassword(true);
        sysUserMapper.insert(user);
        return user;
    }

    public SysUser updateUser(Long id, UserDTO dto) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return null;
        }

        // Protect admin user: cannot change role or disable
        if (isBuiltInAdmin(user.getUsername())) {
            if (dto.getRole() != null && !dto.getRole().equals(user.getRole())) {
                throw new RuntimeException("不能修改管理员账号的角色");
            }
            if (dto.getEnabled() != null && !dto.getEnabled().equals(user.getEnabled())) {
                throw new RuntimeException("不能禁用管理员账号");
            }
        }

        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        if (dto.getRole() != null) {
            validateRole(dto.getRole());
            user.setRole(dto.getRole());
        }
        user.setEnabled(dto.getEnabled());

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            validatePasswordPolicy(dto.getPassword());
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            // Admin reset: force the user to change it at next login
            user.setMustChangePassword(true);
        }

        sysUserMapper.updateById(user);
        return user;
    }

    public boolean deleteUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user != null && isBuiltInAdmin(user.getUsername())) {
            throw new RuntimeException("不能删除管理员账号");
        }
        return sysUserMapper.deleteById(id) > 0;
    }

    public boolean isAdmin(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && "ADMIN".equals(user.getRole());
    }

    public long countAdmins() {
        return sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN")
        );
    }

    public boolean isBuiltInAdmin(String username) {
        return "admin".equals(username);
    }

    public SysUser updateProfile(String username, String oldPassword, String newPassword,
                                  String newPasswordConfirm, String nickname, String email) {
        SysUser user = findByUsername(username);
        if (user == null) return null;

        // If changing password, verify old password and confirmation
        if (newPassword != null && !newPassword.isEmpty()) {
            if (oldPassword == null || oldPassword.isEmpty()) {
                throw new RuntimeException("请输入旧密码");
            }
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new RuntimeException("旧密码不正确");
            }
            if (newPasswordConfirm != null && !newPassword.equals(newPasswordConfirm)) {
                throw new RuntimeException("两次输入的密码不一致");
            }
            validatePasswordPolicy(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
        }

        if (nickname != null) user.setNickname(nickname);
        if (email != null) user.setEmail(email);

        sysUserMapper.updateById(user);
        return user;
    }

    /**
     * 初始化内置管理员账号。首次创建时优先使用环境变量 ADMIN_INIT_PASSWORD，
     * 未设置则生成随机强密码。返回生成的密码（仅当为随机生成时），供启动日志一次性输出。
     */
    public String initAdminUser() {
        SysUser admin = findByUsername("admin");
        if (admin != null) {
            // 修复历史遗留：管理员仍在使用众所周知的默认密码时，标记为必须改密
            if (!Boolean.TRUE.equals(admin.getMustChangePassword())
                    && passwordEncoder.matches("admin123", admin.getPassword())) {
                admin.setMustChangePassword(true);
                sysUserMapper.updateById(admin);
                log.warn("检测到管理员账号仍使用默认密码 admin123，已标记为必须修改密码");
            }
            return null;
        }
        String password;
        boolean generated;
        if (adminInitPassword != null && !adminInitPassword.isEmpty()) {
            password = adminInitPassword;
            generated = false;
        } else {
            password = generateRandomPassword();
            generated = true;
        }
        admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setMustChangePassword(true);
        sysUserMapper.insert(admin);
        return generated ? password : null;
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new RuntimeException("密码长度不能少于8位");
        }
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")) {
            throw new RuntimeException("密码必须包含大写字母、小写字母和数字");
        }
    }

    private void validateRole(String role) {
        if (role == null || role.isEmpty()) {
            throw new RuntimeException("角色不能为空");
        }
        if (!"ADMIN".equals(role) && !"OPERATOR".equals(role) && !"VIEWER".equals(role)) {
            throw new RuntimeException("非法的角色: " + role);
        }
    }

    private String generateRandomPassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnpqrstuvwxyz";
        final String digits = "23456789";
        final String symbols = "!@#$%^&*";
        final String all = upper + lower + digits + symbols;
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(rnd.nextInt(upper.length())));
        sb.append(lower.charAt(rnd.nextInt(lower.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(symbols.charAt(rnd.nextInt(symbols.length())));
        for (int i = 0; i < 12; i++) {
            sb.append(all.charAt(rnd.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
