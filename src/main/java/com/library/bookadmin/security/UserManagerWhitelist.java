package com.library.bookadmin.security;

import com.library.bookadmin.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理白名单：
 * 仅白名单内的登录用户可在前端看到并操作「用户管理」页面。
 * 配置项 app.admin.user-manager-whitelist，多个用户名用逗号分隔。
 */
@Component
public class UserManagerWhitelist {

    private final Set<String> whitelist;

    public UserManagerWhitelist(
            @Value("${app.admin.user-manager-whitelist:admin}") String whitelistConfig) {
        if (StringUtils.hasText(whitelistConfig)) {
            this.whitelist = Arrays.stream(whitelistConfig.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
        } else {
            this.whitelist = Collections.emptySet();
        }
    }

    /** 当前用户名是否在白名单内 */
    public boolean contains(String username) {
        return whitelist.contains(username);
    }

    /** 不在白名单内则抛出业务异常（403 语义） */
    public void assertCanManage(String username) {
        if (!contains(username)) {
            throw new BizException(403, "无权进行用户管理，请联系系统管理员");
        }
    }
}
