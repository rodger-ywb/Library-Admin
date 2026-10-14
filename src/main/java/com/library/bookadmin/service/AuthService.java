package com.library.bookadmin.service;

import com.library.bookadmin.dto.LoginRequest;
import com.library.bookadmin.dto.LoginResponse;
import com.library.bookadmin.entity.SysUser;
import com.library.bookadmin.exception.BizException;
import com.library.bookadmin.repository.SysUserRepository;
import com.library.bookadmin.security.JwtUtil;
import com.library.bookadmin.security.UserManagerWhitelist;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证服务
 * 失败统一提示「用户名或密码错误」，避免用户名枚举
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserManagerWhitelist userManagerWhitelist;

    public LoginResponse login(LoginRequest request) {
        String username = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : "";
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);

        // 用户不存在与密码错误返回同一提示
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        String role = user.getRole();
        if (role == null || role.isBlank()) {
            role = "ADMIN";
        }
        String token = jwtUtil.generateToken(user.getUsername(), role);
        return new LoginResponse(token, user.getUsername(), user.getDisplayName(),
                role, userManagerWhitelist.contains(user.getUsername()));
    }
}
