package com.coldchain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 铅封号登记（点检过程中填写，可改但不能在交班前留空）。
 */
@Data
public class ShiftSealRequest {

    @NotNull(message = "班次ID不能为空")
    private Long shiftId;

    /** 铅封号：允许由一个号改成另一个号，但空白会被服务端拒绝 */
    private String sealNo;
}
