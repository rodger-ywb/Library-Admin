-- =============================================================
-- 书籍管理后台 - 数据库初始化脚本
-- MySQL 8.0+ (utf8mb4, InnoDB)
-- 执行: mysql -uroot -p < init.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `book_admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `book_admin`;

-- -------------------------------------------------------------
-- 书籍信息表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `isbn`          VARCHAR(20)     NOT NULL                COMMENT 'ISBN，去除连字符，唯一',
  `cover_url`     VARCHAR(500)    DEFAULT NULL            COMMENT '封面图URL',
  `cover_width`   INT UNSIGNED    DEFAULT NULL            COMMENT '封面图宽度（像素）',
  `cover_height`  INT UNSIGNED    DEFAULT NULL            COMMENT '封面图高度（像素）',
  `title`         VARCHAR(255)    NOT NULL                COMMENT '书名',
  `author`        VARCHAR(255)    DEFAULT NULL            COMMENT '作者，多人用顿号分隔（允许为空）',
  `publisher`     VARCHAR(100)    DEFAULT NULL            COMMENT '出版社（允许为空）',
  `category_no`   VARCHAR(20)     DEFAULT NULL            COMMENT '分类号（中图法）',
  `category_first` CHAR(1)        DEFAULT NULL            COMMENT '中图法大类字母（分类号首字母 A-Z，等值查询用）',
  `publish_date`  DATE            DEFAULT NULL            COMMENT '出版日期',
  `summary`       TEXT            DEFAULT NULL            COMMENT '内容简介',
  `status`        TINYINT         NOT NULL DEFAULT 3      COMMENT '业务状态：1已上架（含加工中/借出，暂不区分） 3已下架',
  `released_at`   DATETIME        DEFAULT NULL            COMMENT '上架时间，NULL=未上架',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_isbn` (`isbn`),
  KEY `idx_title` (`title`),
  KEY `idx_author` (`author`),
  KEY `idx_publisher` (`publisher`),
  KEY `idx_category_no` (`category_no`),
  KEY `idx_category_first` (`category_first`),
  KEY `idx_publish_date` (`publish_date`),
  KEY `idx_status` (`status`),
  KEY `idx_released_at` (`released_at`),
  CONSTRAINT `chk_book_status` CHECK (`status` IN (1, 2, 3, 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍信息表';

-- -------------------------------------------------------------
-- 后台用户表（登录账号）
-- 初始管理员账号由后端首次启动时自动创建: admin / admin123
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`      VARCHAR(50)     NOT NULL                COMMENT '登录名',
  `password`      VARCHAR(100)    NOT NULL                COMMENT 'BCrypt加密密码',
  `display_name`  VARCHAR(50)     DEFAULT NULL            COMMENT '显示名称',
  `role`          VARCHAR(20)     NOT NULL DEFAULT 'ADMIN' COMMENT '角色：ADMIN管理员 OPERATOR普通操作员',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台用户表';

-- -------------------------------------------------------------
-- 书籍统计表（H5「最新上架」预计算数据，单行）
-- 字段：新书年份(定义当前展示数据范围) / 新书总数 / 每类书总数(每类一列) / 更新时间
-- 写入规则：1条SQL查新书总数 + 每类1条SQL（共22条）
-- 统计口径：状态 IN (1已上架, 2加工中, 4借出)，与 H5 列表展示范围一致（仅排除已下架）
-- 更新时机：后台导入书籍后全量更新；后台上下架后更新对应类别；后台手动按钮全量更新
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `books_stat`;
CREATE TABLE `books_stat` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID（单行表，仅一条记录）',
  `stat_year`     INT             NOT NULL                COMMENT '新书年份（当前展示的数据范围）',
  `total_count`   BIGINT          NOT NULL DEFAULT 0      COMMENT '新书年份内展示范围状态书籍总数',
  `cat_a_count`   INT             NOT NULL DEFAULT 0      COMMENT 'A 马克思主义类 已上架数',
  `cat_b_count`   INT             NOT NULL DEFAULT 0      COMMENT 'B 哲学、宗教类 已上架数',
  `cat_c_count`   INT             NOT NULL DEFAULT 0      COMMENT 'C 社会科学总论类 已上架数',
  `cat_d_count`   INT             NOT NULL DEFAULT 0      COMMENT 'D 政治、法律类 已上架数',
  `cat_e_count`   INT             NOT NULL DEFAULT 0      COMMENT 'E 军事类 已上架数',
  `cat_f_count`   INT             NOT NULL DEFAULT 0      COMMENT 'F 经济类 已上架数',
  `cat_g_count`   INT             NOT NULL DEFAULT 0      COMMENT 'G 文化科学教育体育类 已上架数',
  `cat_h_count`   INT             NOT NULL DEFAULT 0      COMMENT 'H 语言文字类 已上架数',
  `cat_i_count`   INT             NOT NULL DEFAULT 0      COMMENT 'I 文学类 已上架数',
  `cat_j_count`   INT             NOT NULL DEFAULT 0      COMMENT 'J 艺术类 已上架数',
  `cat_k_count`   INT             NOT NULL DEFAULT 0      COMMENT 'K 历史地理类 已上架数',
  `cat_n_count`   INT             NOT NULL DEFAULT 0      COMMENT 'N 自然科学总论类 已上架数',
  `cat_o_count`   INT             NOT NULL DEFAULT 0      COMMENT 'O 数理科学和化学类 已上架数',
  `cat_p_count`   INT             NOT NULL DEFAULT 0      COMMENT 'P 天文学地球科学类 已上架数',
  `cat_q_count`   INT             NOT NULL DEFAULT 0      COMMENT 'Q 生物科学类 已上架数',
  `cat_r_count`   INT             NOT NULL DEFAULT 0      COMMENT 'R 医药卫生类 已上架数',
  `cat_s_count`   INT             NOT NULL DEFAULT 0      COMMENT 'S 农业科学类 已上架数',
  `cat_t_count`   INT             NOT NULL DEFAULT 0      COMMENT 'T 工业技术类 已上架数',
  `cat_u_count`   INT             NOT NULL DEFAULT 0      COMMENT 'U 交通运输类 已上架数',
  `cat_v_count`   INT             NOT NULL DEFAULT 0      COMMENT 'V 航空航天类 已上架数',
  `cat_x_count`   INT             NOT NULL DEFAULT 0      COMMENT 'X 环境安全科学类 已上架数',
  `cat_z_count`   INT             NOT NULL DEFAULT 0      COMMENT 'Z 综合性图书类 已上架数',
  `updated_at`    DATETIME        DEFAULT NULL             COMMENT '统计更新时间',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_stat_year` CHECK (`stat_year` BETWEEN 1900 AND 2100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍统计表（H5最新上架预计算，单行）';

-- -------------------------------------------------------------
-- 统计查询索引建议（books_stat 由后台导入/上下架/手动按钮触发预计算，
-- 统计 SQL 命中以下复合索引可避免全表扫描）
-- 索引方向：status 过滤 → released_at 年份区间 → category_first 大类等值
-- -------------------------------------------------------------
CREATE INDEX `idx_stat_status_year_cat` ON `book` (`status`, `released_at`, `category_first`);
