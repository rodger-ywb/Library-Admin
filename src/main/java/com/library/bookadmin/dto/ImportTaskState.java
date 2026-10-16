package com.library.bookadmin.dto;

import lombok.Data;

/**
 * 大表导入任务状态（异步）
 * 明细（问题行/失败原因）不回传给前端，统一写入服务端日志
 */
@Data
public class ImportTaskState {

    /** 状态：PROCESSING 处理中 / SUCCESS 成功 / FAILED 失败 */
    private String status;

    /** 说明（失败原因或完成提示） */
    private String message;

    /** 参与处理的有效行数 */
    private long total;

    /** 成功新增行数 */
    private long success;

    /** 字段有变更而更新的行数 */
    private long updated;

    /** ISBN 已存在且无变更跳过的行数 */
    private long duplicate;

    /** 写入失败行数 */
    private long failed;

    /** 校验未通过（问题数据）行数 */
    private long invalidRows;
}
