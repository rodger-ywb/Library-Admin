package com.library.bookadmin.controller;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.entity.BooksStat;
import com.library.bookadmin.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书籍统计管理接口（仅管理员）
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService statsService;

    /** 手动全量更新 H5 统计数据（后台书籍管理页按钮触发） */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BooksStat> refresh() {
        return ApiResponse.ok(statsService.refreshAll());
    }
}
