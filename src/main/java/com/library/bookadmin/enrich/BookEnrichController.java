package com.library.bookadmin.enrich;

import com.library.bookadmin.common.ApiResponse;
import com.library.bookadmin.dto.EnrichCandidate;
import com.library.bookadmin.dto.EnrichRequest;
import com.library.bookadmin.dto.PageResult;
import com.library.bookadmin.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 按 ISBN 补齐封面/简介（仅管理员；独立新增接口，不影响既有书籍接口）
 */
@RestController
@RequestMapping("/api/books/enrich")
@RequiredArgsConstructor
public class BookEnrichController {

    /** 候选分页每页上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 单任务 ISBN 数量上限 */
    private static final int MAX_TASK_ISBNS = 200;

    private final EnrichTaskService taskService;
    private final BookEnrichService enrichService;

    /** 缺封面或缺简介的书籍分页列表（供后台选择后批量补齐） */
    @GetMapping("/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<EnrichCandidate>> candidates(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (page < 1) {
            throw new BizException("页码必须大于 0");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.ok(enrichService.findCandidates(page, safeSize));
    }

    /** 提交异步补齐任务，返回 taskId（前端轮询查询进度与结果） */
    @PostMapping("/tasks")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrichTask> submitTask(@RequestBody EnrichRequest request) {
        if (request == null || request.getIsbns() == null || request.getIsbns().isEmpty()) {
            throw new BizException("请至少提供一个 ISBN");
        }
        if (request.getIsbns().size() > MAX_TASK_ISBNS) {
            throw new BizException("单次任务最多处理 " + MAX_TASK_ISBNS + " 个 ISBN，当前 " + request.getIsbns().size() + " 个");
        }
        return ApiResponse.ok(taskService.submit(request.getIsbns()));
    }

    /** 查询补齐任务状态与结果 */
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrichTask> queryTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.ok(taskService.query(taskId));
    }
}
