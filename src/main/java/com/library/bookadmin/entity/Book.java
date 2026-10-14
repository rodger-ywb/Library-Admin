package com.library.bookadmin.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 书籍信息实体，对应表 book
 */
@Data
@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISBN，去除连字符，唯一 */
    @Column(nullable = false, length = 20)
    private String isbn;

    /** 封面图URL */
    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /** 封面图宽度（像素） */
    @Column(name = "cover_width")
    private Integer coverWidth;

    /** 封面图高度（像素） */
    @Column(name = "cover_height")
    private Integer coverHeight;

    /** 书名 */
    @Column(nullable = false, length = 255)
    private String title;

    /** 作者，多人用顿号分隔（允许为空） */
    @Column(length = 255)
    private String author;

    /** 出版社（允许为空） */
    @Column(length = 100)
    private String publisher;

    /** 分类号（中图法） */
    @Column(name = "category_no", length = 20)
    private String categoryNo;

    /** 中图法大类字母（分类号首字母 A-Z，等值索引查询用，由 categoryNo 派生） */
    @Column(name = "category_first", length = 1)
    private String categoryFirst;

    /** 出版日期 */
    @Column(name = "publish_date")
    private LocalDate publishDate;

    /** 内容简介 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 业务状态：1已上架 2加工中 3已下架 */
    @Column(nullable = false)
    private Integer status = 1;

    /** 上架时间，NULL=未上架 */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    /** 创建日期，由数据库默认值维护 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新日期，由数据库默认值维护 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
