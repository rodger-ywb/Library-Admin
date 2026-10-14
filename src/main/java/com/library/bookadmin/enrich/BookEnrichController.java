package com.library.bookadmin.enrich;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.dto.EnrichCandidate;
import com.library.bookadmin.dto.EnrichRequest;
import com.library.bookadmin.dto.EnrichResult;
import com.library.bookadmin.dto.PageResult;
import com.library.bookadmin.exception.BizException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 按 ISBN 补齐封面/简介（仅管理员）。
 * 补齐逻辑已移除，仅保留接口占位：提交任务立即返回空结果，候选列表返回空。
 */
@RestController
@RequestMapping("/api/books/enrich")
public class BookEnrichController {

    /** 候选分页每页上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 单任务 ISBN 数量上限 */
    private static final int MAX_TASK_ISBNS = 200;

    /** 缺封面或缺简介的书籍分页列表（功能停用，返回空列表） */
    @GetMapping("/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<EnrichCandidate>> candidates(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (page < 1) {
            throw new BizException("页码必须大于 0");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.ok(new PageResult<>(List.of(), 0, page, safeSize, 0));
    }

    /** 提交补齐任务（功能停用：校验通过后立即返回空结果，不再异步执行） */
    @PostMapping("/tasks")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrichTask> submitTask(@RequestBody EnrichRequest request) {
        if (request == null || request.getIsbns() == null || request.getIsbns().isEmpty()) {
            throw new BizException("请至少提供一个 ISBN");
        }
        if (request.getIsbns().size() > MAX_TASK_ISBNS) {
            throw new BizException("单次任务最多处理 " + MAX_TASK_ISBNS + " 个 ISBN，当前 " + request.getIsbns().size() + " 个");
        }
        List<String> isbns = request.getIsbns().stream().distinct().toList();
        EnrichResult result = new EnrichResult();
        result.setTotal(isbns.size());
        result.setErrors(List.of("补齐功能已停用，接口保留占位，未执行任何获取"));

        EnrichTask task = new EnrichTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setStatus(EnrichTask.STATUS_SUCCESS);
        task.setTotal(isbns.size());
        task.setProcessed(isbns.size());
        task.setResult(result);
        return ApiResponse.ok(task);
    }

    /** 查询补齐任务状态（功能停用：任意 taskId 均返回空结果） */
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrichTask> queryTask(@PathVariable("taskId") String taskId) {
        EnrichResult result = new EnrichResult();
        result.setTotal(0);
        result.setErrors(List.of("补齐功能已停用，接口保留占位，未执行任何获取"));

        EnrichTask task = new EnrichTask();
        task.setTaskId(taskId);
        task.setStatus(EnrichTask.STATUS_SUCCESS);
        task.setTotal(0);
        task.setProcessed(0);
        task.setResult(result);
        return ApiResponse.ok(task);
    }
}
