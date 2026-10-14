package com.library.bookadmin.repository;

import com.library.bookadmin.entity.BooksStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 书籍统计数据访问层（单行表）
 */
public interface BooksStatRepository extends JpaRepository<BooksStat, Long> {

    /** 取统计行（单行表，按 id 升序取第一条） */
    Optional<BooksStat> findTopByOrderByIdAsc();
}
