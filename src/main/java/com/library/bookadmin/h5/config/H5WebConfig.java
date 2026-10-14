package com.library.bookadmin.h5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * H5 模块 Web 配置：对 /api/public/h5/** 注册防刷限流拦截器
 */
@Configuration
@RequiredArgsConstructor
public class H5WebConfig implements WebMvcConfigurer {

    private final H5RateLimitInterceptor h5RateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(h5RateLimitInterceptor)
                .addPathPatterns("/api/public/h5/**");
    }
}
