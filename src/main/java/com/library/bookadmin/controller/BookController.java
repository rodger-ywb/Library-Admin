package com.library.bookadmin.controller;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.dto.BookQueryRequest;
import com.library.bookadmin.dto.PageResult;
import com.library.bookadmin.dto.UpdateBookStatusRequest;
import com.library.bookadmin.entity.Book;
import com.library.bookadmin.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书籍接口：查询（所有登录用户）/ 上下架（仅管理员）
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /**
     * 多条件分页查询：isbn / 书名 / 作者 / 出版社 / 出版日期范围 / 上架时间范围 / 状态
     */
    @GetMapping
    public ApiResponse<PageResult<Book>> list(BookQueryRequest query) {
        return ApiResponse.ok(bookService.query(query));
    }

    /**
     * 上架/下架（仅管理员）：status = 1 上架 / 3 下架
     * 权限双保险：URL 规则（PUT /api/books/** 需 ADMIN）+ 接口级 @PreAuthorize
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id, @RequestBody UpdateBookStatusRequest request) {
        bookService.updateStatus(id, request.getStatus());
        return ApiResponse.ok();
    }
}
