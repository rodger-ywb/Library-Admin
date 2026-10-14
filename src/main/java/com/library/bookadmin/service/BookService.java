package com.library.bookadmin.service;

import com.library.bookadmin.dto.BookQueryRequest;
import com.library.bookadmin.dto.PageResult;
import com.library.bookadmin.entity.Book;
import com.library.bookadmin.exception.BizException;
import com.library.bookadmin.repository.BookRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍查询服务：多条件组合、参数化查询（Specification 生成 PreparedStatement 参数，免疫 SQL 注入）
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final StatsService statsService;

    public PageResult<Book> query(BookQueryRequest q) {
        int page = (q.getPage() == null || q.getPage() < 1) ? 1 : q.getPage();
        int size = (q.getSize() == null || q.getSize() < 1) ? 10 : Math.min(q.getSize(), 100);

        Specification<Book> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(q.getIsbn())) {
                // ISBN 精确匹配：去空格与连字符后比对
                String isbn = q.getIsbn().trim().replace(" ", "").replace("-", "");
                predicates.add(cb.equal(root.get("isbn"), isbn));
            }
            if (StringUtils.hasText(q.getTitle())) {
                predicates.add(cb.like(root.get("title"), "%" + escapeLike(q.getTitle().trim()) + "%"));
            }
            if (StringUtils.hasText(q.getAuthor())) {
                predicates.add(cb.like(root.get("author"), "%" + escapeLike(q.getAuthor().trim()) + "%"));
            }
            if (StringUtils.hasText(q.getPublisher())) {
                predicates.add(cb.like(root.get("publisher"), "%" + escapeLike(q.getPublisher().trim()) + "%"));
            }
            if (StringUtils.hasText(q.getCategoryFirst())) {
                // 中图法大类字母精确匹配（统一转大写，与 category_first 存储口径一致）
                predicates.add(cb.equal(root.get("categoryFirst"), q.getCategoryFirst().trim().toUpperCase()));
            }
            if (q.getPublishDateStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("publishDate"), q.getPublishDateStart()));
            }
            if (q.getPublishDateEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("publishDate"), q.getPublishDateEnd()));
            }
            if (q.getReleasedAtStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("releasedAt"), q.getReleasedAtStart()));
            }
            if (q.getReleasedAtEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("releasedAt"), q.getReleasedAtEnd()));
            }
            if (q.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), q.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "releasedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Book> result = bookRepository.findAll(spec, pageRequest);
        return new PageResult<>(result.getContent(), result.getTotalElements(), page, size, result.getTotalPages());
    }

    /**
     * 上架/下架：仅允许状态 1（已上架）/ 3（已下架）
     * 上下架改变已上架统计 → 局部更新该书籍所属中图法大类的统计
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 1 && status != 3)) {
            throw new BizException("状态参数不合法：仅支持上架(1)或下架(3)");
        }
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BizException("书籍不存在或已被删除"));
        bookRepository.updateStatus(id, status);

        String code = categoryCodeOf(book.getCategoryNo());
        if (code != null) {
            statsService.refreshCategory(code);
        }
    }

    /** 分类号首字母 → 中图法大类字母（大写），用于统计局部更新 */
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
     * 转义 LIKE 通配符，防止 % / _ 通配注入
     */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
