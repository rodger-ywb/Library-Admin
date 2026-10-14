package com.library.bookadmin.dto;

import lombok.Data;

import java.util.List;

/**
 * 导入确认结果
 */
@Data
public class ImportResult {

    /** 总行数（校验通过、参与处理的行） */
    private int total;

    /** 成功新增行数 */
    private int success;

    /** 因字段有变更而更新的行数 */
    private int updated;

    /** 因 ISBN 已存在且无变更跳过的行数 */
    private int duplicate;

    /** 写入失败行数 */
    private int failed;

    /** 失败/跳过明细 */
    private List<String> errors;
}
