package com.library.bookadmin.h5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * H5 书单卡片数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class H5BookDTO {

    private Long id;

    /** 书名 */
    private String title;

    /** 作者 */
    private String author;

    /** 出版社 */
    private String publisher;

    /** 分类号（即索书号展示，取 "/" 前段） */
    private String categoryNo;

    /** 中图法大类字母：A-Z */
    private String categoryCode;

    /** 中图法大类全称 */
    private String categoryName;

    /** 上架日期 */
    private LocalDate releasedAt;

    /** 馆藏状态：1 已上架 / 4 借出 */
    private Integer status;

    /** 状态文案：在架 / 借出中 */
    private String statusText;
}
