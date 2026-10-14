package com.library.bookadmin.dto;

import lombok.Data;

import java.util.List;

/**
 * 导入预览结果
 */
@Data
public class ImportPreviewResult {

    /** 预览批次ID，确认导入时回传 */
    private String previewId;

    /** 文件解析出的总行数（不含表头与空行） */
    private int totalRows;

    /** 校验通过、可导入的行数 */
    private int validRows;

    /** 校验失败的行数 */
    private int invalidRows;

    /** 有效行中 ISBN 已存在的行数（含待更新与待跳过） */
    private int duplicateRows;

    /** 有效行中 ISBN 已存在且字段有变更、确认导入时将更新的行数 */
    private int updateRows;

    /** 有效行中 ISBN 已存在且字段无变更、确认导入时将跳过的行数 */
    private int skipRows;

    /** 校验通过的行（用于预览展示） */
    private List<ImportPreviewRow> validData;

    /** 校验失败的行（含错误原因，全部返回，前端分页展示） */
    private List<ImportPreviewRow> invalidData;

    /** 提示信息（如：未匹配到的表头列） */
    private List<String> warnings;
}
