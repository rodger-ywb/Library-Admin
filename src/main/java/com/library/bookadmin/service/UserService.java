package com.library.bookadmin.service;

import com.library.bookadmin.dto.CreateUserRequest;
import com.library.bookadmin.entity.SysUser;
import com.library.bookadmin.exception.BizException;
import com.library.bookadmin.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台用户管理服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建后台用户（密码由服务端 BCrypt 加密后落库）
     */
    public SysUser createUser(CreateUserRequest request) {
        String username = request.getUsername().trim();
        if (sysUserRepository.existsByUsername(username)) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim() : username);
        user.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : "ADMIN");
        return sysUserRepository.save(user);
    }

    /**
     * 重置指定用户密码（服务端 BCrypt 加密后更新；用户不存在抛业务异常）
     */
    public SysUser resetPassword(Long userId, String newPassword) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        return sysUserRepository.save(user);
    }

    /**
     * 删除用户（禁止删除当前登录账号自身，防止系统失去管理员）
     */
    public void deleteUser(Long userId, String operatorUsername) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (user.getUsername().equals(operatorUsername)) {
            throw new BizException("不能删除当前登录账号");
        }
        sysUserRepository.delete(user);
    }
}
