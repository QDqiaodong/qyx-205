package com.coldchain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 必检项勾选更新（仅进行中的班可改）。
 */
@Data
public class ShiftCheckRequest {

    @NotNull(message = "班次ID不能为空")
    private Long shiftId;

    /** 必检项编码：APPEARANCE / DOOR_CURTAIN / SEAL */
    @NotNull(message = "必检项编码不能为空")
    private String itemCode;

    /** true 勾上 / false 取消勾选 */
    @NotNull(message = "勾选状态不能为空")
    private Boolean checked;

    private String remark;
}
