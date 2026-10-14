package com.library.bookadmin.service;

import com.library.bookadmin.entity.BooksStat;
import com.library.bookadmin.repository.BookRepository;
import com.library.bookadmin.repository.BooksStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 书籍统计服务：预计算 H5「最新上架」统计数据，写入单行表 books_stat。
 * 口径：库中不区分借出/加工中/上架，统一按已上架（status = 1）处理，仅下架（3）不计入。
 * 写入规则：
 * - 新书总数：1 条 SQL（status = 1 且 releasedAt 落在新书年份内）
 * - 每类总数：每个中图法大类 1 条 SQL（共 22 条，category_first 等值匹配走索引）
 * 更新时机：
 * - 后台导入书籍成功后全量更新（refreshAll）
 * - 后台上下架书籍后更新对应类别（refreshCategory，同时刷新总数）
 * - 后台书籍管理页手动触发全量更新（refreshAll）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    /** 中图法 22 大类字母（books_stat 表对应 cat_x_count 字段） */
    public static final String[] CAT_CODES = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "X", "Z"
    };

    private final BookRepository bookRepository;
    private final BooksStatRepository booksStatRepository;

    /** 全量更新：重算新书年份（最大上架日期年份）、总数、22 类计数 */
    @Transactional
    public BooksStat refreshAll() {
        LocalDateTime max = bookRepository.findMaxReleasedAt();
        int year = (max == null) ? Year.now().getValue() : max.getYear();
        long total = bookRepository.countOnShelfByYear(yearStart(year), yearEnd(year));

        BooksStat stat = getOrCreate();
        stat.setStatYear(year);
        stat.setTotalCount(total);
        for (String code : CAT_CODES) {
            setCount(stat, code, (int) bookRepository.countOnShelfByYearAndCategory(
                    yearStart(year), yearEnd(year), code));
        }
        stat.setUpdatedAt(LocalDateTime.now());
        BooksStat saved = booksStatRepository.save(stat);
        log.info("书籍统计已全量更新: year={}, total={}", year, total);
        return saved;
    }

    /**
     * 局部更新：上下架后重算某大类计数。
     * 上下架同时改变「已上架总数」，因此顺带刷新 total（各 1 条 SQL）。
     */
    @Transactional
    public void refreshCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return;
        }
        String code = categoryCode.trim().toUpperCase();
        BooksStat stat = getOrCreate();
        int year = (stat.getStatYear() != null) ? stat.getStatYear() : Year.now().getValue();

        setCount(stat, code, (int) bookRepository.countOnShelfByYearAndCategory(
                yearStart(year), yearEnd(year), code));
        stat.setTotalCount(bookRepository.countOnShelfByYear(yearStart(year), yearEnd(year)));
        stat.setUpdatedAt(LocalDateTime.now());
        booksStatRepository.save(stat);
        log.info("书籍统计已局部更新: category={}, total={}", code, stat.getTotalCount());
    }

    /** 取当前统计行（未初始化返回 null） */
    public BooksStat getCurrent() {
        return booksStatRepository.findTopByOrderByIdAsc().orElse(null);
    }

    private BooksStat getOrCreate() {
        return booksStatRepository.findTopByOrderByIdAsc().orElseGet(BooksStat::new);
    }

    private LocalDateTime yearStart(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay();
    }

    private LocalDateTime yearEnd(int year) {
        return LocalDate.of(year + 1, 1, 1).atStartOfDay();
    }

    /** 取某大类计数字段值 */
    public static int countOf(BooksStat stat, String code) {
        if (stat == null || code == null) {
            return 0;
        }
        Integer v = switch (code) {
            case "A" -> stat.getCatACount();
            case "B" -> stat.getCatBCount();
            case "C" -> stat.getCatCCount();
            case "D" -> stat.getCatDCount();
            case "E" -> stat.getCatECount();
            case "F" -> stat.getCatFCount();
            case "G" -> stat.getCatGCount();
            case "H" -> stat.getCatHCount();
            case "I" -> stat.getCatICount();
            case "J" -> stat.getCatJCount();
            case "K" -> stat.getCatKCount();
            case "N" -> stat.getCatNCount();
            case "O" -> stat.getCatOCount();
            case "P" -> stat.getCatPCount();
            case "Q" -> stat.getCatQCount();
            case "R" -> stat.getCatRCount();
            case "S" -> stat.getCatSCount();
            case "T" -> stat.getCatTCount();
            case "U" -> stat.getCatUCount();
            case "V" -> stat.getCatVCount();
            case "X" -> stat.getCatXCount();
            case "Z" -> stat.getCatZCount();
            default -> null;
        };
        return v == null ? 0 : v;
    }

    private static void setCount(BooksStat stat, String code, int value) {
        switch (code) {
            case "A" -> stat.setCatACount(value);
            case "B" -> stat.setCatBCount(value);
            case "C" -> stat.setCatCCount(value);
            case "D" -> stat.setCatDCount(value);
            case "E" -> stat.setCatECount(value);
            case "F" -> stat.setCatFCount(value);
            case "G" -> stat.setCatGCount(value);
            case "H" -> stat.setCatHCount(value);
            case "I" -> stat.setCatICount(value);
            case "J" -> stat.setCatJCount(value);
            case "K" -> stat.setCatKCount(value);
            case "N" -> stat.setCatNCount(value);
            case "O" -> stat.setCatOCount(value);
            case "P" -> stat.setCatPCount(value);
            case "Q" -> stat.setCatQCount(value);
            case "R" -> stat.setCatRCount(value);
            case "S" -> stat.setCatSCount(value);
            case "T" -> stat.setCatTCount(value);
            case "U" -> stat.setCatUCount(value);
            case "V" -> stat.setCatVCount(value);
            case "X" -> stat.setCatXCount(value);
            case "Z" -> stat.setCatZCount(value);
            default -> { }
        }
    }

    /** 每类计数字段映射（供 H5 组装 DTO 用） */
    public Map<String, Integer> categoryCountMap(BooksStat stat) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String code : CAT_CODES) {
            map.put(code, countOf(stat, code));
        }
        return map;
    }
}
