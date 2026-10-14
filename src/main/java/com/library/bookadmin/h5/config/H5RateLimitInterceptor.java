package com.library.bookadmin.h5.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H5 只读接口防刷：按「客户端 IP + 接口路径」做 60 秒滑动窗口限流。
 * - 超出窗口上限返回 HTTP 429 + JSON，不进入业务处理
 * - 单机内存实现（ConcurrentHashMap），适用于本系统单次部署形态
 */
@Slf4j
@Component
public class H5RateLimitInterceptor implements HandlerInterceptor {

    /** key = ip|uri → 近 60 秒请求时间戳队列 */
    private static final Map<String, Deque<Long>> WINDOW = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000L;

    /** 每 IP 每接口每分钟允许请求数（application.yml: app.h5-rate-limit.per-minute） */
    @Value("${app.h5-rate-limit.per-minute:120}")
    private int perMinute;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = clientIp(request) + "|" + request.getRequestURI();
        long now = System.currentTimeMillis();

        Deque<Long> queue = WINDOW.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > WINDOW_MS) {
                queue.pollFirst();
            }
            if (queue.size() >= perMinute) {
                allowed = false;
            } else {
                queue.addLast(now);
                allowed = true;
            }
        }

        if (!allowed) {
            log.warn("H5 接口触发限流: {} {}", key, now);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            byte[] body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return false;
        }
        return true;
    }

    /** 取客户端真实 IP：优先 X-Forwarded-For 首段（nginx 反代场景），否则 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
