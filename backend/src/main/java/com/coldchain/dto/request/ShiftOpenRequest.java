package com.coldchain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 开班请求：在哪个库区、白班还是夜班、当班人（交班责任人）。
 */
@Data
public class ShiftOpenRequest {

    @NotBlank(message = "库区不能为空")
    private String zone;

    @NotBlank(message = "班次不能为空")
    private String shiftType;

    @NotBlank(message = "当班人姓名不能为空")
    private String outgoingName;
}
