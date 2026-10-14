package com.library.bookadmin.h5.controller;

import com.library.bookadmin.dto.PageResult;
import com.library.bookadmin.h5.dto.H5BookDTO;
import com.library.bookadmin.h5.dto.H5StatsDTO;
import com.library.bookadmin.h5.service.H5BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * H5「最新上架」只读接口（供微信公众号 H5 / 小程序调用）。
 * - 独立 URL 前缀 /api/public/h5/**：SecurityConfig 放行，无需身份验证
 * - 仅只读查询，复用 Book 实体与 BookRepository
 * - 防刷：H5RateLimitInterceptor 按 IP + 接口维度限流（超限返回 429）
 */
@RestController
@RequestMapping("/api/public/h5")
@RequiredArgsConstructor
public class H5BookController {

    private final H5BookService h5BookService;

    /** 接口一：统计数据（已上架总数 + 年份 + 中图法大类及每类数量） */
    @GetMapping("/stats")
    public H5StatsDTO stats() {
        return h5BookService.stats();
    }

    /** 接口二：书单分页（category 传中图法大类字母 A-Z，空=全部；page 从 1 起；按上架日期倒序） */
    @GetMapping("/books")
    public PageResult<H5BookDTO> books(@RequestParam(name = "category", required = false) String category,
                                       @RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<H5BookDTO> result = h5BookService.books(category, page, Math.min(Math.max(size, 1), 50));
        return PageResult.of(result);
    }
}
