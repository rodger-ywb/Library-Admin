package com.library.bookadmin.h5.service;

import com.library.bookadmin.entity.Book;
import com.library.bookadmin.entity.BooksStat;
import com.library.bookadmin.h5.dto.H5BookDTO;
import com.library.bookadmin.h5.dto.H5CategoryDTO;
import com.library.bookadmin.h5.dto.H5StatsDTO;
import com.library.bookadmin.repository.BookRepository;
import com.library.bookadmin.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H5「最新上架」只读服务：独立于后台业务（不修改 BookService / ImportService）。
 * 仅复用 Book 实体与 BookRepository 做只读查询，统计数据直接读取 books_stat 预计算结果。
 * 展示口径：新书年份（statYear）内、除「已下架(3)」外的书籍全部展示。
 */
@Service
@RequiredArgsConstructor
public class H5BookService {

    private final BookRepository bookRepository;
    private final StatsService statsService;

    /** 可见状态：1 已上架 / 2 加工中 / 4 借出（仅排除已下架） */
    private static final List<Integer> VISIBLE_STATUS = List.of(1, 2, 4);

    /** 中图法 22 大类（字母 → 全称），按字母序固定输出 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();

    static {
        CATEGORY_NAMES.put("A", "马克思主义、列宁主义、毛泽东思想、邓小平理论");
        CATEGORY_NAMES.put("B", "哲学、宗教");
        CATEGORY_NAMES.put("C", "社会科学总论");
        CATEGORY_NAMES.put("D", "政治、法律");
        CATEGORY_NAMES.put("E", "军事");
        CATEGORY_NAMES.put("F", "经济");
        CATEGORY_NAMES.put("G", "文化、科学、教育、体育");
        CATEGORY_NAMES.put("H", "语言、文字");
        CATEGORY_NAMES.put("I", "文学");
        CATEGORY_NAMES.put("J", "艺术");
        CATEGORY_NAMES.put("K", "历史、地理");
        CATEGORY_NAMES.put("N", "自然科学总论");
        CATEGORY_NAMES.put("O", "数理科学和化学");
        CATEGORY_NAMES.put("P", "天文学、地球科学");
        CATEGORY_NAMES.put("Q", "生物科学");
        CATEGORY_NAMES.put("R", "医药、卫生");
        CATEGORY_NAMES.put("S", "农业科学");
        CATEGORY_NAMES.put("T", "工业技术");
        CATEGORY_NAMES.put("U", "交通运输");
        CATEGORY_NAMES.put("V", "航空、航天");
        CATEGORY_NAMES.put("X", "环境科学、安全科学");
        CATEGORY_NAMES.put("Z", "综合性图书");
    }

    /** 状态文案：1 在架 / 2 加工中 / 4 借出中 */
    private static String statusText(Integer status) {
        if (status == null) {
            return "在架";
        }
        return switch (status) {
            case 2 -> "加工中";
            case 4 -> "借出中";
            default -> "在架";
        };
    }

    /** 分类号首字母 → 中图法大类字母（大写） */
    private static String categoryCodeOf(String categoryNo) {
        if (categoryNo == null || categoryNo.isBlank()) {
            return null;
        }
        char c = categoryNo.trim().charAt(0);
        if (Character.isLetter(c)) {
            return String.valueOf(Character.toUpperCase(c));
        }
        return null;
    }

    /**
     * 接口一：统计数据（直接读 books_stat 预计算结果，不实时聚合）
     */
    public H5StatsDTO stats() {
        BooksStat stat = statsService.getCurrent();
        int year = (stat != null && stat.getStatYear() != null) ? stat.getStatYear() : Year.now().getValue();
        long total = (stat != null && stat.getTotalCount() != null) ? stat.getTotalCount() : 0L;

        List<H5CategoryDTO> categories = new ArrayList<>();
        for (Map.Entry<String, String> e : CATEGORY_NAMES.entrySet()) {
            categories.add(new H5CategoryDTO(e.getKey(), e.getValue(), StatsService.countOf(stat, e.getKey())));
        }
        return new H5StatsDTO(total, year, categories);
    }

    /**
     * 接口二：书单分页。
     * 范围：新书年份（books_stat.statYear）内、仅排除已下架；按上架日期倒序。
     */
    public Page<H5BookDTO> books(String category, int page, int size) {
        BooksStat stat = statsService.getCurrent();
        int year = (stat != null && stat.getStatYear() != null) ? stat.getStatYear() : Year.now().getValue();
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = start.plusYears(1);

        Specification<Book> spec = (root, query, cb) -> {
            var visible = cb.and(
                    root.get("status").in(VISIBLE_STATUS),
                    cb.greaterThanOrEqualTo(root.get("releasedAt"), start),
                    cb.lessThan(root.get("releasedAt"), end));
            if (StringUtils.hasText(category)) {
                var code = category.trim().toUpperCase();
                if (CATEGORY_NAMES.containsKey(code)) {
                    // 中图法大类字母（category_first 列）等值匹配，可走索引，避免 LIKE 前缀扫描
                    return cb.and(visible, cb.equal(root.get("categoryFirst"), code));
                }
                // 非法分类：恒 false，返回空
                return cb.isFalse(cb.literal(true));
            }
            return visible;
        };
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size,
                Sort.by(Sort.Direction.DESC, "releasedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return bookRepository.findAll(spec, pageable).map(this::toDTO);
    }

    private H5BookDTO toDTO(Book b) {
        // 大类字母优先取 category_first（导入/更新时已派生）；兼容历史数据回退到分类号首字母
        String categoryNo = b.getCategoryNo();
        String code = b.getCategoryFirst();
        if (!StringUtils.hasText(code)) {
            code = categoryCodeOf(categoryNo);
        }
        String suojia = categoryNo;
        int slash = categoryNo == null ? -1 : categoryNo.indexOf('/');
        if (slash > 0) {
            suojia = categoryNo.substring(0, slash);
        }
        return new H5BookDTO(
                b.getId(),
                b.getTitle(),
                b.getAuthor(),
                b.getPublisher(),
                suojia,
                code,
                code == null ? "" : CATEGORY_NAMES.getOrDefault(code, ""),
                b.getReleasedAt() == null ? null : b.getReleasedAt().toLocalDate(),
                b.getStatus(),
                statusText(b.getStatus()));
    }
}
