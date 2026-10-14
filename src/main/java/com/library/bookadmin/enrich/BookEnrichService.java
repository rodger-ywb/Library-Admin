package com.library.bookadmin.enrich;

import com.library.bookadmin.dto.EnrichCandidate;
import com.library.bookadmin.dto.EnrichResult;
import com.library.bookadmin.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * 按 ISBN 补齐书籍封面图与内容简介（新增独立功能，不改动既有导入/查询逻辑）。
 *
 * 数据源：豆瓣读书 ISBN 直达页为主（一次请求同时返回封面+简介），当当搜索+详情页兜底；
 * 原 Google Books / Open Library 在国内网络不可达（实测连接失败），已移除。
 * 更新策略：仅补齐 book 表中 cover_url / summary 为空的字段，已有值不覆盖。
 * 安全：ISBN 白名单正则校验、外部请求仅限固定 https 域名、SQL 全部参数化、单次数量上限。
 * 执行：本服务为纯同步方法（逐 ISBN 顺序获取），异步调度由 EnrichTaskService 的任务线程完成。
 */
@Service
@RequiredArgsConstructor
public class BookEnrichService {

    private static final Logger log = LoggerFactory.getLogger(BookEnrichService.class);

    /** ISBN 规则与导入一致：13 位数字；10 位末位 0-9/X；9 位缺校验位 */
    private static final Pattern ISBN_PATTERN = Pattern.compile("\\d{13}|\\d{9}[0-9Xx]?");

    /** 单次处理 ISBN 数量上限 */
    private static final int MAX_ISBNS = 200;

    /** 数据库 IN 查询分批大小 */
    private static final int DB_BATCH = 100;

    /** 外部请求间隔（毫秒），避免高频触发第三方限流 */
    private static final long REQUEST_INTERVAL_MS = 200;

    private static final String DOUBAN_ISBN_URL = "https://book.douban.com/isbn/";
    private static final String DANGDANG_SEARCH_URL = "https://search.dangdang.com/?key=";
    private static final String DANGDANG_PRODUCT_URL = "https://product.dangdang.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 book-admin-enrich/1.0";

    /** 豆瓣封面：mainpic 区域的第一个 img src */
    private static final Pattern DOUBAN_COVER = Pattern.compile("id=\"mainpic\"[\\s\\S]{0,2000}?<img[^>]*?src=\"([^\"]+)\"");
    /** 豆瓣简介：class="intro" 块（可能多段） */
    private static final Pattern DOUBAN_INTRO = Pattern.compile("class=\"intro\"[^>]*>([\\s\\S]*?)</(?:span|div)>");
    /** 当当搜索页商品链接 */
    private static final Pattern DANGDANG_LINK = Pattern.compile("href=\"(?:https?:)?//product\\.dangdang\\.com/(\\d+)\\.html\"");
    /** 当当详情页封面 */
    private static final Pattern DANGDANG_COVER = Pattern.compile("id=\"largePic\"[^>]*src=\"([^\"]+)\"|class=\"main_img\"[^>]*src=\"([^\"]+)\"");
    /** 当当详情页简介 */
    private static final Pattern DANGDANG_DESC = Pattern.compile("class=\"descrip\"[^>]*>([\\s\\S]*?)</");

    private final JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 按 ISBN 列表补齐封面/简介（同步执行，异步任务通过进度回调回报 processed/total）。
     */
    public EnrichResult enrich(List<String> isbnInputs) {
        return enrich(isbnInputs, null);
    }

    /**
     * 按 ISBN 列表补齐封面/简介；progress 回调用于异步任务进度上报（processed, total）。
     */
    public EnrichResult enrich(List<String> isbnInputs, BiConsumer<Integer, Integer> progress) {
        EnrichResult result = new EnrichResult();

        // 1. 规范化 + 去重 + 合法性校验
        Set<String> isbns = new LinkedHashSet<>();
        for (String raw : isbnInputs) {
            if (raw == null) {
                continue;
            }
            String isbn = raw.trim().replace(" ", "").replace("-", "").replace("–", "").toUpperCase();
            if (isbn.isEmpty()) {
                continue;
            }
            if (!ISBN_PATTERN.matcher(isbn).matches()) {
                result.setInvalid(result.getInvalid() + 1);
                result.getErrors().add("ISBN「" + raw.trim() + "」：格式不正确，已忽略");
                continue;
            }
            isbns.add(isbn);
        }
        if (isbns.size() > MAX_ISBNS) {
            throw new com.library.bookadmin.exception.BizException("单次最多处理 " + MAX_ISBNS + " 个 ISBN，当前 " + isbns.size() + " 个");
        }
        result.setTotal(isbns.size());

        // 2. 分批查出库中记录（isbn -> 当前封面/简介），判断哪些需要补齐
        Map<String, String[]> existing = findExisting(isbns);

        // 3. 逐 ISBN 补齐
        long lastRequestAt = 0;
        int processed = 0;
        for (String isbn : isbns) {
            processed++;
            if (progress != null) {
                progress.accept(processed, isbns.size());
            }
            String[] cur = existing.get(isbn);
            if (cur == null) {
                result.setNotInDb(result.getNotInDb() + 1);
                result.getErrors().add("ISBN " + isbn + "：库中不存在，已跳过");
                continue;
            }
            boolean hasCover = hasText(cur[0]);
            boolean hasSummary = hasText(cur[1]);
            if (hasCover && hasSummary) {
                result.setAlreadyFilled(result.getAlreadyFilled() + 1);
                continue;
            }

            // 外部请求限速
            long wait = lastRequestAt + REQUEST_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAt = System.currentTimeMillis();

            String cover = null;
            String summary = null;
            Map<String, String> fetched = fetchAll(isbn);
            if (!hasCover) {
                cover = fetched.get("cover");
            }
            if (!hasSummary) {
                summary = fetched.get("summary");
            }

            if (cover == null && summary == null) {
                result.setNotFound(result.getNotFound() + 1);
                continue;
            }
            updateBook(isbn, cover, summary);
            result.setEnriched(result.getEnriched() + 1);
        }
        log.info("按 ISBN 补齐封面/简介完成: total={}, enriched={}, alreadyFilled={}, notFound={}, notInDb={}, invalid={}",
                result.getTotal(), result.getEnriched(), result.getAlreadyFilled(),
                result.getNotFound(), result.getNotInDb(), result.getInvalid());
        return result;
    }

    // ==================== 数据源（豆瓣为主，当当兜底；国内可达，Google/OpenLibrary 不可达已移除） ====================

    /**
     * 获取封面与简介：优先豆瓣（一次请求同时返回），未命中或解析失败时当当兜底。
     */
    private Map<String, String> fetchAll(String isbn) {
        Map<String, String> data = fetchDouban(isbn);
        if (!data.isEmpty()) {
            return data;
        }
        return fetchDangdang(isbn);
    }

    /** 豆瓣 ISBN 直达页：封面 mainpic + 简介 intro；请求失败/未命中返回空 Map */
    private Map<String, String> fetchDouban(String isbn) {
        try {
            String html = getHtml(DOUBAN_ISBN_URL + isbn + "/");
            if (html == null) {
                return Map.of();
            }
            Map<String, String> data = new HashMap<>(2);
            java.util.regex.Matcher coverMatcher = DOUBAN_COVER.matcher(html);
            if (coverMatcher.find() && coverMatcher.group(1) != null) {
                data.put("cover", coverMatcher.group(1));
            }
            java.util.regex.Matcher introMatcher = DOUBAN_INTRO.matcher(html);
            StringBuilder summary = new StringBuilder();
            while (introMatcher.find() && introMatcher.group(1) != null) {
                summary.append(introMatcher.group(1));
            }
            String text = stripHtml(summary.toString());
            if (!text.isEmpty()) {
                data.put("summary", text);
            }
            return data;
        } catch (Exception e) {
            log.debug("豆瓣查询失败 isbn={}: {}", isbn, e.getMessage());
            return Map.of();
        }
    }

    /** 当当兜底：搜索页找商品链接 → 详情页取封面/简介；任一步失败返回空 Map */
    private Map<String, String> fetchDangdang(String isbn) {
        try {
            String searchHtml = getHtml(DANGDANG_SEARCH_URL + isbn);
            if (searchHtml == null) {
                return Map.of();
            }
            java.util.regex.Matcher linkMatcher = DANGDANG_LINK.matcher(searchHtml);
            if (!linkMatcher.find()) {
                return Map.of();
            }
            String productId = linkMatcher.group(1);
            String detailHtml = getHtml(DANGDANG_PRODUCT_URL + productId + ".html");
            if (detailHtml == null) {
                return Map.of();
            }
            Map<String, String> data = new HashMap<>(2);
            java.util.regex.Matcher coverMatcher = DANGDANG_COVER.matcher(detailHtml);
            if (coverMatcher.find()) {
                String cover = coverMatcher.group(1) != null ? coverMatcher.group(1) : coverMatcher.group(2);
                if (cover != null) {
                    data.put("cover", cover.startsWith("//") ? "https:" + cover : cover);
                }
            }
            java.util.regex.Matcher descMatcher = DANGDANG_DESC.matcher(detailHtml);
            if (descMatcher.find() && descMatcher.group(1) != null) {
                String text = stripHtml(descMatcher.group(1));
                if (!text.isEmpty()) {
                    data.put("summary", text);
                }
            }
            return data;
        } catch (Exception e) {
            log.debug("当当查询失败 isbn={}: {}", isbn, e.getMessage());
            return Map.of();
        }
    }

    /** GET 请求返回页面文本；网络失败/超时返回 null */
    private String getHtml(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            log.debug("请求失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /** 去除 HTML 标签并压缩空白，截断到 2000 字符 */
    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    // ==================== 数据库 ====================

    /** 分批查询库中记录：isbn -> {coverUrl, summary} */
    private Map<String, String[]> findExisting(Set<String> isbns) {
        Map<String, String[]> map = new HashMap<>();
        List<String> list = new ArrayList<>(isbns);
        for (int i = 0; i < list.size(); i += DB_BATCH) {
            List<String> part = list.subList(i, Math.min(i + DB_BATCH, list.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(part.size(), "?"));
            jdbcTemplate.query(
                    "SELECT isbn, cover_url, summary FROM book WHERE isbn IN (" + placeholders + ")",
                    rs -> {
                        map.put(rs.getString("isbn"),
                                new String[]{rs.getString("cover_url"), rs.getString("summary")});
                    },
                    part.toArray());
        }
        return map;
    }

    /** 只补齐空字段：封面/简介已有值时不覆盖 */
    private void updateBook(String isbn, String cover, String summary) {
        jdbcTemplate.update(
                "UPDATE book SET "
                        + "cover_url = CASE WHEN cover_url IS NULL OR cover_url = '' THEN ? ELSE cover_url END, "
                        + "summary = CASE WHEN summary IS NULL OR summary = '' THEN ? ELSE summary END "
                        + "WHERE isbn = ?",
                cover, summary, isbn);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    // ==================== 待补齐候选查询 ====================

    /** 缺封面或缺简介的书籍分页列表（按 id 倒序，新书在前） */
    public PageResult<EnrichCandidate> findCandidates(int page, int size) {
        int offset = (page - 1) * size;
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book WHERE cover_url IS NULL OR cover_url = '' OR summary IS NULL OR summary = ''",
                Integer.class);
        long totalCount = total == null ? 0 : total;
        List<EnrichCandidate> records = jdbcTemplate.query(
                "SELECT id, isbn, title, author, publisher, cover_url, summary FROM book "
                        + "WHERE cover_url IS NULL OR cover_url = '' OR summary IS NULL OR summary = '' "
                        + "ORDER BY id DESC LIMIT ?, ?",
                (rs, rowNum) -> {
                    EnrichCandidate c = new EnrichCandidate();
                    c.setId(rs.getLong("id"));
                    c.setIsbn(rs.getString("isbn"));
                    c.setTitle(rs.getString("title"));
                    c.setAuthor(rs.getString("author"));
                    c.setPublisher(rs.getString("publisher"));
                    c.setCoverUrl(rs.getString("cover_url"));
                    c.setSummary(rs.getString("summary"));
                    c.setMissingCover(!hasText(c.getCoverUrl()));
                    c.setMissingSummary(!hasText(c.getSummary()));
                    return c;
                },
                offset, size);
        long pages = (totalCount + size - 1) / size;
        return new PageResult<>(records, totalCount, page, size, pages);
    }
}
