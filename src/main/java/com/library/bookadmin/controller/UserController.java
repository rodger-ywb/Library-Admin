package com.library.bookadmin.controller;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.dto.CreateUserRequest;
import com.library.bookadmin.dto.UpdatePasswordRequest;
import com.library.bookadmin.entity.SysUser;
import com.library.bookadmin.repository.SysUserRepository;
import com.library.bookadmin.security.UserManagerWhitelist;
import com.library.bookadmin.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台用户管理接口（仅 ADMIN 角色可访问，且须在白名单内，由 SecurityConfig + UserManagerWhitelist 控制）
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SysUserRepository sysUserRepository;
    private final UserManagerWhitelist userManagerWhitelist;

    /**
     * 用户列表（密码字段已序列化忽略，不返回）
     */
    @GetMapping
    public ApiResponse<List<SysUser>> list(Authentication authentication) {
        userManagerWhitelist.assertCanManage(authentication.getName());
        return ApiResponse.ok(sysUserRepository.findAll(Sort.by(Sort.Direction.DESC, "id")));
    }

    /**
     * 添加后台用户（密码由服务端 BCrypt 加密存储）
     */
    @PostMapping
    public ApiResponse<SysUser> create(@Valid @RequestBody CreateUserRequest request,
                                       Authentication authentication) {
        userManagerWhitelist.assertCanManage(authentication.getName());
        return ApiResponse.ok(userService.createUser(request));
    }

    /**
     * 重置用户密码（服务端 BCrypt 加密存储；仅白名单管理员可操作）
     */
    @PutMapping("/{id}/password")
    public ApiResponse<SysUser> resetPassword(@PathVariable("id") Long id,
                                              @Valid @RequestBody UpdatePasswordRequest request,
                                              Authentication authentication) {
        userManagerWhitelist.assertCanManage(authentication.getName());
        return ApiResponse.ok(userService.resetPassword(id, request.getNewPassword()));
    }

    /**
     * 删除用户（仅白名单管理员可操作；禁止删除当前登录账号自身）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id, Authentication authentication) {
        userManagerWhitelist.assertCanManage(authentication.getName());
        userService.deleteUser(id, authentication.getName());
        return ApiResponse.ok();
    }
}
