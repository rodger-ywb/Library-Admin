package com.library.bookadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建后台用户请求（管理员调用）
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
    private String password;

    @Size(max = 50, message = "显示名称最长 50 个字符")
    private String displayName;

    /** 角色：ADMIN 管理员 / OPERATOR 普通操作员，默认 ADMIN */
    @Pattern(regexp = "^(ADMIN|OPERATOR)$", message = "角色仅支持 ADMIN 或 OPERATOR")
    private String role = "ADMIN";
}
