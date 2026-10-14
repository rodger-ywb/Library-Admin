package com.library.bookadmin.enrich;

import com.library.bookadmin.dto.EnrichResult;
import lombok.Data;

/**
 * 补齐封面/简介异步任务（内存态；服务重启后任务丢失，可重新提交）
 */
@Data
public class EnrichTask {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String taskId;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    /** 总处理数 */
    private int total;

    /** 已处理数 */
    private int processed;

    /** 失败原因（FAILED 时） */
    private String message;

    /** 最终结果（SUCCESS 时） */
    private EnrichResult result;
}
