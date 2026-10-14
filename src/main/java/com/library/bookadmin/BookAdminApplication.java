package com.library.bookadmin;

import com.library.bookadmin.entity.SysUser;
import com.library.bookadmin.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 书籍管理后台 - 服务端启动类
 */
@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class BookAdminApplication implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.display-name}")
    private String adminDisplayName;

    public static void main(String[] args) {
        SpringApplication.run(BookAdminApplication.class, args);
    }

    /**
     * 首次启动时自动创建默认管理员账号（表内无任何用户时）
     */
    @Override
    public void run(String... args) {
        if (sysUserRepository.count() == 0) {
            SysUser admin = new SysUser();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setDisplayName(adminDisplayName);
            sysUserRepository.save(admin);
            log.warn("已自动创建默认管理员账号: {} / {}（生产环境请立即修改密码）", adminUsername, adminPassword);
        }
    }
}
