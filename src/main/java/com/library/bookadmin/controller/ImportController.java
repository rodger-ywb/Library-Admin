package com.library.bookadmin.controller;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.dto.ImportConfirmRequest;
import com.library.bookadmin.dto.ImportPreviewResult;
import com.library.bookadmin.dto.ImportResult;
import com.library.bookadmin.dto.ImportTaskState;
import com.library.bookadmin.dto.ImportTaskSubmitResult;
import com.library.bookadmin.service.ImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * 上传 Excel 并返回预览结果（不落库）；≤10MB 的小文件走此接口
     */
    @PostMapping("/import/preview")
    public ApiResponse<ImportPreviewResult> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.preview(file));
    }

    /**
     * 大文件（>10MB，数十万行）异步导入：接口立即返回任务 ID，后台线程流式分批导入，
     * 问题数据明细不回传，统一写入服务端日志
     */
    @PostMapping("/import/large")
    public ApiResponse<ImportTaskSubmitResult> submitLarge(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.submitLarge(file));
    }

    /**
     * 查询大表导入任务状态（仅统计数字，不含明细）
     */
    @GetMapping("/import/tasks/{taskId}")
    public ApiResponse<ImportTaskState> getTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.ok(importService.getImportTask(taskId));
    }

    /**
     * 确认导入预览批次（按 100 条/批写入数据库）
     */
    @PostMapping("/import/confirm")
    public ApiResponse<ImportResult> confirm(@RequestBody @Valid ImportConfirmRequest request) {
        return ApiResponse.ok(importService.confirm(request.getPreviewId()));
    }
}
