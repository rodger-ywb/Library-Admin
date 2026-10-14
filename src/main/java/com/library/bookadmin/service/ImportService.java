package com.library.bookadmin.service;

import com.alibaba.excel.EasyExcel;
import com.library.bookadmin.dto.ImportPreviewResult;
import com.library.bookadmin.dto.ImportPreviewRow;
import com.library.bookadmin.dto.ImportResult;
import com.library.bookadmin.entity.Book;
import com.library.bookadmin.exception.BizException;
import com.library.bookadmin.repository.BookRepository;
import com.library.bookadmin.util.BookFieldMapping;
import com.library.bookadmin.util.ExcelMapListener;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 书籍 Excel 导入服务
 * 流程：上传解析 -> 表头字段匹配 -> 逐行校验 -> 返回预览 -> 确认后按批(默认100条/批)写入
 * 安全措施：文件类型/大小校验、解析异常隔离、ISBN 唯一性去重、逐批事务提交
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 10000;
    private static final Set<String> EXCEL_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyy"));

    private static final List<DateTimeFormatter> DATETIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));

    private final BookRepository bookRepository;
    private final TransactionTemplate transactionTemplate;
    private final StatsService statsService;

    @Value("${app.import.batch-size:100}")
    private int batchSize;

    @Value("${app.import.preview-expire-seconds:1800}")
    private long previewExpireSeconds;

    /** 预览批次缓存（单机内存实现；多实例部署时应替换为 Redis） */
    private final Map<String, PreviewCache> previewCache = new ConcurrentHashMap<>();

    // ==================== 预览 ====================

    public ImportPreviewResult preview(MultipartFile file) {
        // 1. 文件基础校验
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !isExcelName(filename)) {
            throw new BizException("仅支持 .xlsx / .xls 格式的 Excel 文件");
        }
        String contentType = file.getContentType();
        if (contentType != null && !EXCEL_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BizException("文件类型不正确，请上传 Excel 文件");
        }
        if (file.isEmpty()) {
            throw new BizException("文件内容为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("文件超过大小限制（最大 10MB）");
        }

        // 2. 解析 Excel（第一行为表头）
        ExcelMapListener listener = new ExcelMapListener();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in)
                    .sheet()
                    .headRowNumber(1)
                    .registerReadListener(listener)
                    .doRead();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel 解析失败, file={}", filename, e);
            throw new BizException("Excel 解析失败，请检查文件是否损坏或格式是否正确");
        }

        if (listener.getHeadMap().isEmpty()) {
            throw new BizException("未读取到表头行，请确认第一行为字段名");
        }
        if (listener.getDataRows().size() > MAX_ROWS) {
            throw new BizException("单次导入最多支持 " + MAX_ROWS + " 行数据");
        }

        // 3. 表头 -> 表字段匹配
        Map<String, Integer> columnByField = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        listener.getHeadMap().forEach((colIdx, headerText) -> {
            String field = BookFieldMapping.matchField(headerText);
            if (field != null) {
                columnByField.putIfAbsent(field, colIdx);
            } else if (StringUtils.hasText(headerText)) {
                warnings.add("表头「" + headerText + "」未匹配到任何字段，该列将被忽略");
            }
        });
        if (!columnByField.containsKey("isbn") || !columnByField.containsKey("title")) {
            throw new BizException("模板必须包含「ISBN」与「书名」两列（表头需与字段名对应）");
        }

        // 4. 逐行校验
        List<ImportPreviewRow> valid = new ArrayList<>();
        List<ImportPreviewRow> invalid = new ArrayList<>();
        int excelRow = 1; // 表头占第 1 行
        for (Map<Integer, String> row : listener.getDataRows()) {
            excelRow++;
            if (isEmptyRow(row)) {
                continue;
            }
            ImportPreviewRow previewRow = validateRow(row, columnByField, excelRow);
            if (previewRow.isValid()) {
                valid.add(previewRow);
            } else {
                invalid.add(previewRow);
            }
        }
        // 5. 幂等检测：批量查出库中已存在的书籍实体，逐行对比 6 个业务字段
        //    NEW=新增 / UPDATE=字段有变更待更新 / SKIP=无变更跳过
        //    IN 列表分批查询（每批 500），避免超大 IN 拉长 SQL / 触碰 max_allowed_packet、提升索引命中效率
        //    同一 ISBN 在文件中出现多行（馆藏复本）时，对比与导入以最后一行数据为准：
        //    否则复本行（如上架时间差几秒）会与库中值反复判定"更新"，永不幂等
        Map<String, ImportPreviewRow> lastRowByIsbn = new LinkedHashMap<>();
        for (ImportPreviewRow row : valid) {
            lastRowByIsbn.put(row.getIsbn(), row);
        }
        valid = new ArrayList<>(lastRowByIsbn.values());
        Map<String, Book> existingBooks = new HashMap<>();
        if (!valid.isEmpty()) {
            List<String> isbns = valid.stream().map(ImportPreviewRow::getIsbn).distinct().collect(Collectors.toList());
            final int inBatchSize = 500;
            for (int i = 0; i < isbns.size(); i += inBatchSize) {
                List<String> part = isbns.subList(i, Math.min(i + inBatchSize, isbns.size()));
                for (Book b : bookRepository.findExistingBooksByIsbnIn(part)) {
                    existingBooks.put(b.getIsbn(), b);
                }
            }
        }
        int duplicateRows = 0;
        int updateRows = 0;
        int skipRows = 0;
        for (ImportPreviewRow row : valid) {
            Book exist = existingBooks.get(row.getIsbn());
            if (exist == null) {
                row.setImportMode("NEW");
                row.setModeText("新增");
                continue;
            }
            row.setIsbnExists(true);
            duplicateRows++;
            List<String> changed = changedFields(exist, row);
            if (changed.isEmpty()) {
                row.setImportMode("SKIP");
                row.setModeText("跳过");
                skipRows++;
            } else {
                row.setImportMode("UPDATE");
                row.setModeText("更新");
                row.setUpdateReason(String.join("、", changed));
                updateRows++;
            }
        }

        // 6. 生成预览批次并缓存（全部无效也正常返回预览，让用户看到失败明细）
        String previewId = UUID.randomUUID().toString().replace("-", "");
        previewCache.put(previewId, new PreviewCache(valid, existingBooks,
                System.currentTimeMillis() + previewExpireSeconds * 1000));

        ImportPreviewResult result = new ImportPreviewResult();
        result.setPreviewId(previewId);
        result.setTotalRows(valid.size() + invalid.size());
        result.setValidRows(valid.size());
        result.setInvalidRows(invalid.size());
        result.setDuplicateRows(duplicateRows);
        result.setUpdateRows(updateRows);
        result.setSkipRows(skipRows);
        result.setValidData(valid);
        // 校验失败行全部返回，由前端分页展示
        result.setInvalidData(invalid);
        result.setWarnings(warnings);
        return result;
    }

    // ==================== 确认导入 ====================

    public ImportResult confirm(String previewId) {
        PreviewCache cache = previewCache.get(previewId);
        if (cache == null) {
            throw new BizException("预览批次不存在，请重新上传文件");
        }
        if (cache.getExpireAt() < System.currentTimeMillis()) {
            previewCache.remove(previewId);
            throw new BizException("预览已过期，请重新上传文件");
        }

        List<ImportPreviewRow> rows = cache.getValidData();
        Map<String, Book> existingBooks = cache.getExistingBooks();
        int total = rows.size();
        int success = 0;
        int updated = 0;
        int duplicate = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        // 分批处理，每批独立事务提交（默认 100 条/批）
        for (int i = 0; i < rows.size(); i += batchSize) {
            List<ImportPreviewRow> batch = rows.subList(i, Math.min(i + batchSize, rows.size()));
            BatchResult br = transactionTemplate.execute(status -> saveBatch(batch, existingBooks));
            success += br.success;
            updated += br.updated;
            duplicate += br.duplicate;
            failed += br.failed;
            errors.addAll(br.errors);
        }
        previewCache.remove(previewId);

        // 新增或更新成功后，全量刷新 H5 统计数据（新书年份/总数/22 类计数）
        if (success > 0 || updated > 0) {
            statsService.refreshAll();
        }

        ImportResult result = new ImportResult();
        result.setTotal(total);
        result.setSuccess(success);
        result.setUpdated(updated);
        result.setDuplicate(duplicate);
        result.setFailed(failed);
        result.setErrors(errors);
        log.info("书籍导入完成: total={}, success={}, updated={}, duplicate={}, failed={}",
                total, success, updated, duplicate, failed);
        return result;
    }

    /**
     * 处理一个批次（同一事务内执行）：
     * NEW 新增写入；UPDATE 更新已存在记录（仅覆盖 6 个业务字段）；SKIP 无变更直接跳过
     */
    private BatchResult saveBatch(List<ImportPreviewRow> batch, Map<String, Book> existingBooks) {
        int success = 0;
        int updated = 0;
        int duplicate = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Set<String> batchIsbns = new HashSet<>();

        for (ImportPreviewRow row : batch) {
            String isbn = row.getIsbn();
            try {
                String mode = row.getImportMode() == null ? "NEW" : row.getImportMode();
                if ("UPDATE".equals(mode)) {
                    Book exist = existingBooks.get(isbn);
                    if (exist == null) {
                        // 防御：预览时存在、确认时消失（理论上不会发生）
                        duplicate++;
                        errors.add("第 " + row.getRowNum() + " 行：ISBN " + isbn + " 已不存在，已跳过");
                        continue;
                    }
                    applyRow(exist, row);
                    bookRepository.save(exist);
                    updated++;
                } else if ("SKIP".equals(mode)) {
                    duplicate++;
                } else {
                    // NEW：新增
                    if (batchIsbns.contains(isbn) || bookRepository.existsByIsbn(isbn)) {
                        duplicate++;
                        errors.add("第 " + row.getRowNum() + " 行：ISBN " + isbn + " 已存在，已跳过");
                        continue;
                    }
                    batchIsbns.add(isbn);
                    bookRepository.save(toBook(row));
                    success++;
                }
            } catch (DataIntegrityViolationException e) {
                duplicate++;
                errors.add("第 " + row.getRowNum() + " 行：ISBN " + isbn + " 已存在，已跳过");
            } catch (Exception e) {
                failed++;
                errors.add("第 " + row.getRowNum() + " 行（《" + brief(row.getTitle()) + "》，ISBN " + isbn + "）：写入失败，请检查该行数据");
                log.warn("导入单行写入失败 row={}", row.getRowNum(), e);
            }
        }
        return new BatchResult(success, updated, duplicate, failed, errors);
    }

    // ==================== 校验与转换 ====================

    private ImportPreviewRow validateRow(Map<Integer, String> row,
                                          Map<String, Integer> columnByField,
                                          int rowNum) {
        ImportPreviewRow r = new ImportPreviewRow();
        r.setRowNum(rowNum);
        List<String> errors = new ArrayList<>();

        // ISBN：必填，去除空格与连字符后校验。规则：13 位纯数字；10 位（末位可为 0-9 或 X）；9 位（去掉校验位的 10 位 ISBN）
        String isbn = normalizeIsbn(cell(row, columnByField, "isbn"));
        if (!StringUtils.hasText(isbn)) {
            errors.add("ISBN「" + brief(cell(row, columnByField, "isbn")) + "」：不能为空");
        } else if (!isbn.matches("\\d{13}|\\d{9}[0-9Xx]?")) {
            errors.add("ISBN「" + brief(isbn) + "」：格式不正确（支持 13 位数字、10 位 ISBN（末位可为 0-9 或 X）、9 位缺校验位）");
        } else {
            r.setIsbn(isbn.toUpperCase());
        }

        // 书名 / 作者 / 出版社：必填
        String title = cell(row, columnByField, "title");
        if (!StringUtils.hasText(title)) {
            errors.add("书名「" + brief(title) + "」：不能为空");
        } else if (title.length() > 255) {
            errors.add("书名「" + brief(title) + "」：超长（当前 " + title.length() + " 字符，上限 255）");
        } else {
            r.setTitle(title);
        }

        // 作者（允许为空；非空时校验超长）
        String author = cell(row, columnByField, "author");
        if (StringUtils.hasText(author) && author.length() > 255) {
            errors.add("作者「" + brief(author) + "」：超长（当前 " + author.length() + " 字符，上限 255）");
        } else {
            r.setAuthor(StringUtils.hasText(author) ? author : null);
        }

        // 出版社（允许为空；非空时校验超长）
        String publisher = cell(row, columnByField, "publisher");
        if (StringUtils.hasText(publisher) && publisher.length() > 100) {
            errors.add("出版社「" + brief(publisher) + "」：超长（当前 " + publisher.length() + " 字符，上限 100）");
        } else {
            r.setPublisher(StringUtils.hasText(publisher) ? publisher : null);
        }

        // 分类号（可选，超长截断保护）
        String categoryNo = cell(row, columnByField, "categoryNo");
        if (StringUtils.hasText(categoryNo)) {
            r.setCategoryNo(categoryNo.length() > 20 ? categoryNo.substring(0, 20) : categoryNo);
        }

        // 出版日期（可选）
        String publishDate = cell(row, columnByField, "publishDate");
        if (StringUtils.hasText(publishDate)) {
            try {
                r.setPublishDate(parseDate(publishDate));
            } catch (DateTimeParseException e) {
                errors.add("出版日期「" + brief(publishDate) + "」：格式不正确（支持 yyyy-MM-dd / yyyy/MM/dd / 纯年份 yyyy）");
            }
        }

        // 上架时间（可选）
        String releasedAt = cell(row, columnByField, "releasedAt");
        if (StringUtils.hasText(releasedAt)) {
            try {
                r.setReleasedAt(parseDateTime(releasedAt));
            } catch (DateTimeParseException e) {
                errors.add("上架时间「" + brief(releasedAt) + "」：格式不正确（支持 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd）");
            }
        }

        // 状态（可选，默认已上架；任意值均可映射，未知值按已下架处理）
        String status = cell(row, columnByField, "status");
        if (StringUtils.hasText(status)) {
            Integer code = parseStatus(status);
            r.setStatusCode(code);
            r.setStatusText(statusText(code));
        } else {
            r.setStatusCode(1);
            r.setStatusText("已上架");
        }

        // 封面图（可选）
        String coverUrl = cell(row, columnByField, "coverUrl");
        if (StringUtils.hasText(coverUrl)) {
            if (coverUrl.length() > 500) {
                errors.add("封面图URL「" + brief(coverUrl) + "」：超长（当前 " + coverUrl.length() + " 字符，上限 500）");
            } else {
                r.setCoverUrl(coverUrl);
            }
        }

        // 封面宽 / 高（可选）
        String coverWidth = cell(row, columnByField, "coverWidth");
        Integer width = parsePositiveInt(coverWidth);
        if (width != null) {
            r.setCoverWidth(width);
        } else if (StringUtils.hasText(coverWidth)) {
            errors.add("封面图宽度「" + brief(coverWidth) + "」：应为正整数");
        }
        String coverHeight = cell(row, columnByField, "coverHeight");
        Integer height = parsePositiveInt(coverHeight);
        if (height != null) {
            r.setCoverHeight(height);
        } else if (StringUtils.hasText(coverHeight)) {
            errors.add("封面图高度「" + brief(coverHeight) + "」：应为正整数");
        }

        // 内容简介（可选）
        r.setSummary(cell(row, columnByField, "summary"));

        if (errors.isEmpty()) {
            r.setValid(true);
        } else {
            r.setValid(false);
            r.setErrorMessage(String.join("；", errors));
        }
        return r;
    }

    private Book toBook(ImportPreviewRow r) {
        Book book = new Book();
        applyRow(book, r);
        return book;
    }

    /** 将导入行数据应用到 Book（新增/更新共用；更新时保留原 id） */
    private void applyRow(Book book, ImportPreviewRow r) {
        book.setIsbn(r.getIsbn());
        book.setTitle(r.getTitle());
        book.setAuthor(r.getAuthor());
        book.setPublisher(r.getPublisher());
        book.setCategoryNo(r.getCategoryNo());
        book.setCategoryFirst(categoryFirstOf(r.getCategoryNo()));
        book.setPublishDate(r.getPublishDate());
        book.setSummary(r.getSummary());
        book.setStatus(r.getStatusCode() == null ? 1 : r.getStatusCode());
        book.setReleasedAt(r.getReleasedAt());
        book.setCoverUrl(r.getCoverUrl());
        book.setCoverWidth(r.getCoverWidth());
        book.setCoverHeight(r.getCoverHeight());
    }

    /**
     * 对比已存在记录与导入行，返回有变更的业务字段中文名列表。
     * 对比字段：书名 / 作者 / 出版社 / 状态 / 分类号 / 上架时间（时间按秒精度比较）。
     */
    private List<String> changedFields(Book exist, ImportPreviewRow r) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(exist.getTitle(), r.getTitle())) changed.add("书名");
        if (!Objects.equals(exist.getAuthor(), r.getAuthor())) changed.add("作者");
        if (!Objects.equals(exist.getPublisher(), r.getPublisher())) changed.add("出版社");
        if (!Objects.equals(exist.getStatus(), r.getStatusCode() == null ? 1 : r.getStatusCode())) changed.add("状态");
        if (!Objects.equals(exist.getCategoryNo(), r.getCategoryNo())) changed.add("分类号");
        if (!Objects.equals(truncSecond(exist.getReleasedAt()), truncSecond(r.getReleasedAt()))) changed.add("上架时间");
        return changed;
    }

    private LocalDateTime truncSecond(LocalDateTime t) {
        return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS);
    }

    /** 分类号首字母 → 中图法大类字母（大写 A-Z，非字母返回 null） */
    private String categoryFirstOf(String categoryNo) {
        if (!StringUtils.hasText(categoryNo)) {
            return null;
        }
        char c = categoryNo.trim().charAt(0);
        return Character.isLetter(c) ? String.valueOf(Character.toUpperCase(c)) : null;
    }

    // ==================== 工具方法 ====================

    private String cell(Map<Integer, String> row, Map<String, Integer> columnByField, String field) {
        Integer colIdx = columnByField.get(field);
        if (colIdx == null) {
            return "";
        }
        String value = row.get(colIdx);
        return value == null ? "" : value.trim();
    }

    private boolean isEmptyRow(Map<Integer, String> row) {
        return row.values().stream().allMatch(v -> !StringUtils.hasText(v));
    }

    private String normalizeIsbn(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().replace(" ", "").replace("-", "").replace("–", "");
    }

    /** 错误提示用的值摘要：空值显示「空」，超长截断到 30 字符 */
    private String brief(String text) {
        if (!StringUtils.hasText(text)) {
            return "空";
        }
        String v = text.trim();
        return v.length() > 30 ? v.substring(0, 30) + "…" : v;
    }

    /**
     * 出版日期解析（容错设计）：
     * 1. 完整 年-月-日：2026 年 2 月 1 日 / 2025-03-05 / 2021.3.5
     * 2. 年-月：2024-04 / 2021.3 / 2026 年 3 月（日默认 1）
     * 3. 纯年份：2016 / 2029- / 2021~（尾部杂字符自动忽略，月日默认 1 月 1 日）
     */
    private static final Pattern P_DATE_YMD = Pattern.compile("^(\\d{4})[年\\-/.\u3000\\s]+(\\d{1,2})[月\\-/.\u3000\\s]+(\\d{1,2})日?");
    private static final Pattern P_DATE_YM = Pattern.compile("^(\\d{4})[年\\-/.\u3000\\s]+(\\d{1,2})月?");
    private static final Pattern P_DATE_Y = Pattern.compile("^(\\d{4})");

    private LocalDate parseDate(String text) {
        String v = text.trim();

        // 1) 年-月-日
        Matcher m = P_DATE_YMD.matcher(v);
        if (m.find()) {
            LocalDate d = tryDate(m.group(1), m.group(2), m.group(3));
            if (d != null) return d;
        }
        // 2) 年-月（日默认 1）
        m = P_DATE_YM.matcher(v);
        if (m.find()) {
            LocalDate d = tryDate(m.group(1), m.group(2), "1");
            if (d != null) return d;
        }
        // 3) 纯年份（月日默认 1 月 1 日，尾部字符如 - ~ 版 等自动忽略）
        m = P_DATE_Y.matcher(v);
        if (m.find()) {
            LocalDate d = tryDate(m.group(1), "1", "1");
            if (d != null) return d;
        }

        // 兜底：标准格式列表
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        throw new DateTimeParseException("日期格式不正确", v, 0);
    }

    /** 按 年/月/日 组合成 LocalDate；数值非法（如月份 0 或 13）返回 null 交由下一级规则处理 */
    private LocalDate tryDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year.trim()),
                    Integer.parseInt(month.trim()),
                    Integer.parseInt(day.trim()));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String text) {
        String v = text.trim();
        for (DateTimeFormatter formatter : DATETIME_FORMATS) {
            try {
                TemporalAccessor accessor = formatter.parse(v);
                if (accessor.query(TemporalQueries.localTime()) == null) {
                    return LocalDate.from(accessor).atStartOfDay();
                }
                return LocalDateTime.from(accessor);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        throw new DateTimeParseException("时间格式不正确", v, 0);
    }

    private Integer parseStatus(String text) {
        String v = text.trim();
        return switch (v) {
            case "1", "已上架", "在架" -> 1;
            case "2", "加工中", "在编" -> 2;
            case "4", "借出" -> 4;
            // 其他任何值一律按已下架处理，不报错
            default -> 3;
        };
    }

    private String statusText(int code) {
        return switch (code) {
            case 1 -> "已上架";
            case 2 -> "加工中";
            case 4 -> "借出";
            default -> "已下架";
        };
    }

    private Integer parsePositiveInt(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isExcelName(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    // ==================== 内部结构 ====================

    @Getter
    @AllArgsConstructor
    private static class PreviewCache {
        private final List<ImportPreviewRow> validData;
        /** 预览时查出的库中已存在书籍（isbn → Book），确认导入时用于比对更新 */
        private final Map<String, Book> existingBooks;
        private final long expireAt;
    }

    @AllArgsConstructor
    private static class BatchResult {
        private final int success;
        private final int updated;
        private final int duplicate;
        private final int failed;
        private final List<String> errors;
    }
}
