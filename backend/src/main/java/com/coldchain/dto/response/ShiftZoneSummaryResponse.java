package com.coldchain.dto.response;

import lombok.Data;

/**
 * 库区交接班总览行：每个库区一条，用于“按库区反查当班人”。
 */
@Data
public class ShiftZoneSummaryResponse {

    private String zone;

    /** 该库区是否有进行中的班 */
    private Boolean hasOpenShift;

    /** 当前当班人：进行中即开班人；无进行中班时为 null（上一班已交完） */
    private String currentOutgoingName;

    /** 进行中班的班次：白班/夜班；无进行中班为 null */
    private String currentShiftType;

    /** 进行中班ID；无进行中班为 null。与点检明细、交班流水用同一个ID对齐 */
    private Long currentShiftId;

    /** 进行中班开班时间 */
    private String currentOpenedAt;

    /** 最近一次已交班的接班人（上一班交给了谁），没有历史则为 null */
    private String lastIncomingName;

    /** 累计已交班次数 */
    private Long handedCount;
}
