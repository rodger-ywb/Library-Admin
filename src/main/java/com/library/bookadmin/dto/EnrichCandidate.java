package com.library.bookadmin.dto;

import lombok.Data;

/**
 * 待补齐候选书籍（缺封面或缺简介）
 */
@Data
public class EnrichCandidate {

    private Long id;

    private String isbn;

    private String title;

    private String author;

    private String publisher;

    /** 封面图地址（空 = 缺封面） */
    private String coverUrl;

    /** 内容简介（空 = 缺简介） */
    private String summary;

    /** 是否缺封面 */
    private boolean missingCover;

    /** 是否缺简介 */
    private boolean missingSummary;
}
