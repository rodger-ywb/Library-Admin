package com.library.bookadmin.repository;

import com.library.bookadmin.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 书籍数据访问层
 * 动态查询基于 Specification（参数化查询），天然免疫 SQL 注入
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);

    /** 新书年份：取最大的上架日期（无数据返回 null） */
    @Query("select max(b.releasedAt) from Book b")
    LocalDateTime findMaxReleasedAt();

    /**
     * 新书总数：新书年份内、展示范围状态（1 已上架 / 2 加工中 / 4 借出）的书籍总数（1 条 SQL）。
     * 统计口径与 H5 列表展示范围一致（VISIBLE_STATUS）。
     */
    @Query("select count(b) from Book b where b.releasedAt >= ?1 and b.releasedAt < ?2 and b.status in ?3")
    long countOnShelfByYear(LocalDateTime start, LocalDateTime end, Collection<Integer> statuses);

    /**
     * 每类书总数：新书年份内某中图法大类、展示范围状态的书籍总数
     * （每类 1 条 SQL，category_first 等值匹配走索引）。
     */
    @Query("select count(b) from Book b where b.releasedAt >= ?1 and b.releasedAt < ?2 " +
            "and b.categoryFirst = ?3 and b.status in ?4")
    long countOnShelfByYearAndCategory(LocalDateTime start, LocalDateTime end, String code,
                                       Collection<Integer> statuses);

    /** 批量查询库中已存在的书籍（导入幂等：对比字段以决定更新或跳过） */
    @Query("select b from Book b where b.isbn in ?1")
    List<Book> findExistingBooksByIsbnIn(Collection<String> isbns);

    /** 上下架：原子更新状态并刷新更新日期 */
    @Modifying
    @Query("update Book b set b.status = ?2, b.updatedAt = CURRENT_TIMESTAMP where b.id = ?1")
    int updateStatus(Long id, Integer status);
}
