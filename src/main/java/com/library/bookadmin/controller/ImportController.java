package com.library.bookadmin.controller;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.dto.ImportConfirmRequest;
import com.library.bookadmin.dto.ImportPreviewResult;
import com.library.bookadmin.dto.ImportResult;
import com.library.bookadmin.service.ImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 书籍导入接口
 * 流程：上传解析预览 -> 前端确认 -> 服务端分批写入（默认 100 条/批）
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * 上传 Excel 并返回预览结果（不落库）
     */
    @PostMapping("/import/preview")
    public ApiResponse<ImportPreviewResult> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.preview(file));
    }

    /**
     * 确认导入预览批次（按 100 条/批写入数据库）
     */
    @PostMapping("/import/confirm")
    public ApiResponse<ImportResult> confirm(@RequestBody @Valid ImportConfirmRequest request) {
        return ApiResponse.ok(importService.confirm(request.getPreviewId()));
    }
}
