package com.library.bookadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应（JWT）
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String username;
    private String displayName;
    /** 角色：ADMIN / OPERATOR */
    private String role;
    /** 是否在用户管理白名单内（前端据此显示/隐藏用户管理入口） */
    private boolean userManager;
}
