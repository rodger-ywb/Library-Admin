package com.library.bookadmin.enrich;

import com.library.bookadmin.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 补齐封面/简介异步任务管理：提交任务 → 后台线程池执行 → 前端轮询查询进度与结果。
 * 任务保存在内存中，服务重启后丢失（可重新提交）。
 */
@Service
@RequiredArgsConstructor
public class EnrichTaskService {

    private static final Logger log = LoggerFactory.getLogger(EnrichTaskService.class);

    /** 并发执行任务数上限 */
    private static final int CONCURRENT_TASKS = 2;

    private final ConcurrentHashMap<String, EnrichTask> tasks = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TASKS);

    private final BookEnrichService enrichService;

    /** 提交异步补齐任务，立即返回 taskId */
    public EnrichTask submit(List<String> isbns) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        EnrichTask task = new EnrichTask();
        task.setTaskId(taskId);
        task.setStatus(EnrichTask.STATUS_RUNNING);
        task.setTotal(isbns.size());
        tasks.put(taskId, task);

        executor.submit(() -> {
            try {
                EnrichTask running = tasks.get(taskId);
                if (running == null) {
                    return;
                }
                com.library.bookadmin.dto.EnrichResult result =
                        enrichService.enrich(isbns, (processed, total) -> {
                            EnrichTask t = tasks.get(taskId);
                            if (t != null) {
                                t.setProcessed(processed);
                            }
                        });
                EnrichTask done = tasks.get(taskId);
                if (done != null) {
                    done.setStatus(EnrichTask.STATUS_SUCCESS);
                    done.setProcessed(done.getTotal());
                    done.setResult(result);
                }
                log.info("补齐任务完成 taskId={}, total={}", taskId, isbns.size());
            } catch (Exception e) {
                log.error("补齐任务执行失败 taskId={}", taskId, e);
                EnrichTask failed = tasks.get(taskId);
                if (failed != null) {
                    failed.setStatus(EnrichTask.STATUS_FAILED);
                    failed.setMessage(e.getMessage());
                }
            }
        });
        return task;
    }

    /** 查询任务状态（不存在时抛业务异常） */
    public EnrichTask query(String taskId) {
        EnrichTask task = tasks.get(taskId);
        if (task == null) {
            throw new BizException("任务不存在或已失效（服务重启后任务需重新提交）");
        }
        return task;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
