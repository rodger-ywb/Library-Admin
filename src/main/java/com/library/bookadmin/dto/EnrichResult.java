package com.library.bookadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 ISBN 补齐封面/简介结果
 */
@Data
public class EnrichResult {

    /** 输入的 ISBN 数量（去重后） */
    private int total;

    /** 成功补齐条数（封面或简介至少补上一项） */
    private int enriched;

    /** 库中该 ISBN 封面与简介已齐全、无需补齐的条数 */
    private int alreadyFilled;

    /** 库中存在但外部数据源均未找到任何信息 */
    private int notFound;

    /** ISBN 不在库中的条数 */
    private int notInDb;

    /** 校验不合法被忽略的条数 */
    private int invalid;

    /** 失败/忽略明细（ISBN + 原因） */
    private List<String> errors = new ArrayList<>();
}
