package com.library.bookadmin.h5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * H5 顶部横幅数据：新书上架总数 + 年份
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class H5OverviewDTO {

    /** 已上架图书总数（Banner 主数字） */
    private long total;

    /** 年份（Banner 装饰数字） */
    private int year;
}
