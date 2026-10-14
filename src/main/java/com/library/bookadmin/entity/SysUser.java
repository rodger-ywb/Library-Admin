package com.library.bookadmin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户实体，对应表 sys_user
 */
@Data
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 加密密码（禁止序列化输出，防止接口泄露哈希） */
    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String password;

    /** 显示名称 */
    @Column(name = "display_name", length = 50)
    private String displayName;

    /** 角色：ADMIN 管理员 / OPERATOR 普通操作员（存量数据为空时按 ADMIN 处理） */
    @Column(nullable = false, length = 20)
    private String role = "ADMIN";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
