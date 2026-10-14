package com.library.bookadmin.h5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * H5 统计数据（接口一）：已上架总数 + 年份 + 中图法 22 大类（含每类数量）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class H5StatsDTO {

    /** 已上架图书总数（Banner 主数字） */
    private long total;

    /** 年份（Banner 装饰数字） */
    private int year;

    /** 中图法 22 大类及每类「非下架」图书数量 */
    private List<H5CategoryDTO> categories;
}
