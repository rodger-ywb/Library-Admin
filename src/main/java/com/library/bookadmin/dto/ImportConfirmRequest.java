package com.library.bookadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 导入确认请求
 */
@Data
public class ImportConfirmRequest {

    /** 预览阶段返回的批次ID */
    @NotBlank(message = "预览批次ID不能为空")
    private String previewId;
}
