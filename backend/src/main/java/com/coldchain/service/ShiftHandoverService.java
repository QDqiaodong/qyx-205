package com.coldchain.service;

import com.coldchain.dto.request.ShiftCheckRequest;
import com.coldchain.dto.request.ShiftHandoverSubmitRequest;
import com.coldchain.dto.request.ShiftOpenRequest;
import com.coldchain.dto.request.ShiftSealRequest;
import com.coldchain.dto.response.ShiftHandoverResponse;
import com.coldchain.dto.response.ShiftZoneSummaryResponse;

import java.util.List;

/**
 * 库区交接班。
 * 规则：
 * 1. 当班人先按库区把必检项勾齐（外观、门帘、铅封号），再填接班人姓名和交班说明才能交班；
 *    漏勾、铅封号空着、接班人跟当班人同名，这一班都交不出去。
 * 2. 交成功后点检勾选、交班流水、按库区反查当班人三处以同一 shiftId 对齐成同一班。
 * 3. 已交班的班冻结：不许再改勾选，也不许把铅封号改成空。
 * 4. 同一库区上一班没交完时，后来的人再开新班必须被拦住，先开的班保持原样。
 */
public interface ShiftHandoverService {

    /** 开班：同库区有进行中的班时直接拒绝 */
    ShiftHandoverResponse openShift(ShiftOpenRequest request);

    /** 班次详情（含必检项勾选明细） */
    ShiftHandoverResponse getShift(Long id);

    /** 某库区全部班次（最新在前） */
    List<ShiftHandoverResponse> getShiftsByZone(String zone);

    /** 交班流水：全部已交班班次 */
    List<ShiftHandoverResponse> getHandoverLogs();

    /** 库区总览：按库区反查当班人 */
    List<ShiftZoneSummaryResponse> getZoneSummary();

    /** 勾选/取消一项必检（已交班的班拒绝） */
    ShiftHandoverResponse updateCheck(ShiftCheckRequest request);

    /** 登记/修改铅封号（已交班的班拒绝；不允许清空） */
    ShiftHandoverResponse updateSealNo(ShiftSealRequest request);

    /** 交班：勾齐 + 铅封号非空 + 接班人非空且不同于当班人，否则整笔拒绝 */
    ShiftHandoverResponse submitHandover(ShiftHandoverSubmitRequest request);
}
