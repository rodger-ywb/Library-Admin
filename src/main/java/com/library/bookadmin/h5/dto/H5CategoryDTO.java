package com.library.bookadmin.h5.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中图法大类：code（如 I）+ 名称 + 该大类在架图书数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class H5CategoryDTO {

    /** 大类字母：A-Z */
    private String code;

    /** 中图法大类全称 */
    private String name;

    /** 该大类下在架（含借出）图书数量 */
    private int count;
}
