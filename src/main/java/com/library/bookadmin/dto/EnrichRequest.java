package com.library.bookadmin.dto;

import lombok.Data;

import java.util.List;

/**
 * 按 ISBN 补齐封面/简介请求（新增功能，独立于既有导入流程）
 */
@Data
public class EnrichRequest {

    /** ISBN 列表（支持 13 位数字、10 位末位 0-9/X、9 位缺校验位；自动去重） */
    private List<String> isbns;
}
