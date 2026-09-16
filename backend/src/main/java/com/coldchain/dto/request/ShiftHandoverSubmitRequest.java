package com.coldchain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 交班请求：三项必检勾齐 + 铅封号非空 + 接班人非空且不同于当班人，缺一不可。
 */
@Data
public class ShiftHandoverSubmitRequest {

    @NotNull(message = "班次ID不能为空")
    private Long shiftId;

    /** 接班人姓名 */
    private String incomingName;

    /** 交班说明（口头交代落到纸面） */
    private String handoverNote;
}
