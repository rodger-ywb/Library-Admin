package com.library.bookadmin.dto;

import lombok.Data;

/**
 * 上下架请求：status 仅允许 1（上架）/ 3（下架）
 */
@Data
public class UpdateBookStatusRequest {

    private Integer status;
}
