package com.library.bookadmin.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EasyExcel 动态表头监听器
 * - headRowNumber(1)：第一行作为表头，不进入数据
 * - 数据行以「列索引 -> 单元格字符串」的 Map 形式回调
 */
public class ExcelMapListener extends AnalysisEventListener<Map<Integer, String>> {

    /** 表头：列索引 -> 表头文本 */
    private Map<Integer, String> headMap = new HashMap<>();

    /** 数据行列表（不含表头与空行） */
    private final List<Map<Integer, String>> dataRows = new ArrayList<>();

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        this.headMap = new HashMap<>();
        headMap.forEach((idx, value) -> this.headMap.put(idx, value == null ? "" : value.trim()));
    }

    @Override
    public void invoke(Map<Integer, String> row, AnalysisContext context) {
        dataRows.add(row);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 无需处理
    }

    public Map<Integer, String> getHeadMap() {
        return headMap;
    }

    public List<Map<Integer, String>> getDataRows() {
        return dataRows;
    }
}
