package com.library.bookadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 书籍统计表（单行）：H5「最新上架」统计数据预计算存放。
 * - statYear：新书年份（当前展示的数据范围）
 * - totalCount：新书年份对应的已上架书籍总数
 * - catXCount：新书年份内每个中图法大类的已上架书籍数（每类一个字段）
 * - updatedAt：最近一次统计更新时间
 */
@Entity
@Getter
@Setter
@Table(name = "books_stat")
public class BooksStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 新书年份（当前展示的数据范围） */
    @Column(name = "stat_year")
    private Integer statYear;

    /** 新书年份内已上架书籍总数 */
    @Column(name = "total_count")
    private Long totalCount;

    @Column(name = "cat_a_count")
    private Integer catACount;
    @Column(name = "cat_b_count")
    private Integer catBCount;
    @Column(name = "cat_c_count")
    private Integer catCCount;
    @Column(name = "cat_d_count")
    private Integer catDCount;
    @Column(name = "cat_e_count")
    private Integer catECount;
    @Column(name = "cat_f_count")
    private Integer catFCount;
    @Column(name = "cat_g_count")
    private Integer catGCount;
    @Column(name = "cat_h_count")
    private Integer catHCount;
    @Column(name = "cat_i_count")
    private Integer catICount;
    @Column(name = "cat_j_count")
    private Integer catJCount;
    @Column(name = "cat_k_count")
    private Integer catKCount;
    @Column(name = "cat_n_count")
    private Integer catNCount;
    @Column(name = "cat_o_count")
    private Integer catOCount;
    @Column(name = "cat_p_count")
    private Integer catPCount;
    @Column(name = "cat_q_count")
    private Integer catQCount;
    @Column(name = "cat_r_count")
    private Integer catRCount;
    @Column(name = "cat_s_count")
    private Integer catSCount;
    @Column(name = "cat_t_count")
    private Integer catTCount;
    @Column(name = "cat_u_count")
    private Integer catUCount;
    @Column(name = "cat_v_count")
    private Integer catVCount;
    @Column(name = "cat_x_count")
    private Integer catXCount;
    @Column(name = "cat_z_count")
    private Integer catZCount;

    /** 最近一次统计更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
