package com.library.bookadmin.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导入预览单行数据（前端预览表格展示用）
 */
@Data
public class ImportPreviewRow {

    /** 行号（Excel 中的行号，含表头行） */
    private int rowNum;

    /** 原始 ISBN */
    private String isbn;

    private String title;

    private String author;

    private String publisher;

    private String categoryNo;

    private LocalDate publishDate;

    private String summary;

    /** 状态码：1已上架 2加工中 3已下架 */
    private Integer statusCode;

    /** 状态显示文案：已上架 / 加工中 / 已下架 */
    private String statusText;

    private LocalDateTime releasedAt;

    private String coverUrl;

    private Integer coverWidth;

    private Integer coverHeight;

    /** 校验是否通过（true=可导入，false=存在错误被剔除） */
    private boolean valid;

    /** 该行 ISBN 在库中已存在 */
    private boolean isbnExists;

    /** 导入处理方式：NEW=新增 / UPDATE=更新 / SKIP=跳过（无变更） */
    private String importMode;

    /** 处理方式显示文案：新增 / 更新 / 跳过 */
    private String modeText;

    /** 更新原因（变更字段列表，仅 UPDATE 时有值），如「作者、出版社、状态」 */
    private String updateReason;

    /** 校验失败原因（多行以分号连接） */
    private String errorMessage;
}
