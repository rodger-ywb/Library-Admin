package com.library.bookadmin.util;

import java.util.Map;

/**
 * Excel 表头与书籍表字段的别名映射
 * 导入时根据第一行表头文本匹配到对应表字段（支持中英文别名）
 */
public final class BookFieldMapping {

    private BookFieldMapping() {
    }

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("isbn", "isbn"),
            Map.entry("国际标准书号", "isbn"),
            Map.entry("标准书号", "isbn"),
            Map.entry("书号", "isbn"),
            Map.entry("书名", "title"),
            Map.entry("标题", "title"),
            Map.entry("题名", "title"),
            Map.entry("作者", "author"),
            Map.entry("责任者", "author"),
            Map.entry("出版社", "publisher"),
            Map.entry("出版单位", "publisher"),
            Map.entry("分类号", "categoryNo"),
            Map.entry("中图法分类号", "categoryNo"),
            Map.entry("分类", "categoryNo"),
            Map.entry("出版日期", "publishDate"),
            Map.entry("出版时间", "publishDate"),
            Map.entry("内容简介", "summary"),
            Map.entry("简介", "summary"),
            Map.entry("状态", "status"),
            Map.entry("业务状态", "status"),
            Map.entry("上架时间", "releasedAt"),
            Map.entry("入库日期", "releasedAt"),
            Map.entry("封面图", "coverUrl"),
            Map.entry("封面图地址", "coverUrl"),
            Map.entry("封面图url", "coverUrl"),
            Map.entry("封面", "coverUrl"),
            Map.entry("封面图宽度", "coverWidth"),
            Map.entry("封面宽度", "coverWidth"),
            Map.entry("宽度", "coverWidth"),
            Map.entry("封面图高度", "coverHeight"),
            Map.entry("封面高度", "coverHeight"),
            Map.entry("高度", "coverHeight")
    );

    /**
     * 规范化表头文本：去首尾空格、去内部空格、转小写
     */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().replace(" ", "").replace("\u00A0", "").toLowerCase();
    }

    /**
     * 表头文本 -> 表字段名；无法匹配返回 null
     */
    public static String matchField(String header) {
        return HEADER_ALIASES.get(normalizeHeader(header));
    }
}
