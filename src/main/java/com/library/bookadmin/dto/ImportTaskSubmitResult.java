package com.library.bookadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 大表导入提交结果：接口立即返回，不等待导入完成
 */
@Data
@AllArgsConstructor
public class ImportTaskSubmitResult {

    /** 任务 ID，用于查询导入状态 */
    private String taskId;

    /** 初始状态：PROCESSING */
    private String status;

    /** 提示信息 */
    private String message;
}
