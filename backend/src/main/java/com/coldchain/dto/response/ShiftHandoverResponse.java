package com.coldchain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 一个班次（含必检项明细）。点检勾选、交班流水、按库区反查当班人三处读的都是这同一份数据，
 * 以同一 shiftId 对齐成同一班。
 */
@Data
public class ShiftHandoverResponse {

    private Long id;
    private String zone;
    private String shiftType;

    /** 1进行中 2已交班 */
    private Integer status;
    private String statusText;

    private String outgoingName;
    private String incomingName;
    private String sealNo;
    private String handoverNote;

    private String openedAt;
    private String handedAt;

    /** 必检项总数（固定 3 项） */
    private Integer totalItems;

    /** 已勾选数；进行中等于总数才算勾齐 */
    private Integer checkedItems;

    /** 三项是否全部勾齐 */
    private Boolean allChecked;

    private List<ShiftCheckItemResponse> checkItems;
}
