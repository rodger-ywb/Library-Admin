package com.library.bookadmin.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 书籍多条件分页查询请求
 */
@Data
public class BookQueryRequest {

    /** ISBN 精确匹配 */
    private String isbn;

    /** 书名模糊查询 */
    private String title;

    /** 作者模糊查询 */
    private String author;

    /** 出版社模糊查询 */
    private String publisher;

    /** 出版日期范围-起 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishDateStart;

    /** 出版日期范围-止 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishDateEnd;

    /** 上架时间范围-起 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime releasedAtStart;

    /** 上架时间范围-止 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime releasedAtEnd;

    /** 业务状态：1已上架 2加工中 3已下架 */
    private Integer status;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 10;
}
