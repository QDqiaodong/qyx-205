package com.coldchain.controller;

import com.coldchain.dto.request.ShiftCheckRequest;
import com.coldchain.dto.request.ShiftHandoverSubmitRequest;
import com.coldchain.dto.request.ShiftOpenRequest;
import com.coldchain.dto.request.ShiftSealRequest;
import com.coldchain.dto.response.ApiResponse;
import com.coldchain.dto.response.ShiftHandoverResponse;
import com.coldchain.dto.response.ShiftZoneSummaryResponse;
import com.coldchain.service.ShiftHandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库区交接班。
 * 交接班只动 shift_* 三张表，不提供任何修改货位编码配对、托盘承重/上架的入口。
 */
@RestController
@RequestMapping("/api/shift")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ShiftHandoverController {

    private final ShiftHandoverService shiftHandoverService;

    /** 库区总览：按库区反查当前当班人、进行中班ID、上一班接班人 */
    @GetMapping("/zones")
    public ApiResponse<List<ShiftZoneSummaryResponse>> getZoneSummary() {
        return ApiResponse.success(shiftHandoverService.getZoneSummary());
    }

    /** 开班：同库区上一班没交完会被拦住 */
    @PostMapping("/open")
    public ApiResponse<ShiftHandoverResponse> openShift(@Valid @RequestBody ShiftOpenRequest request) {
        return ApiResponse.success("开班成功", shiftHandoverService.openShift(request));
    }

    /** 班次详情（含三项必检勾选明细、铅封号、交班说明） */
    @GetMapping("/{id}")
    public ApiResponse<ShiftHandoverResponse> getShift(@PathVariable Long id) {
        return ApiResponse.success(shiftHandoverService.getShift(id));
    }

    /** 某库区全部班次（最新在前） */
    @GetMapping("/zone/{zone}")
    public ApiResponse<List<ShiftHandoverResponse>> getShiftsByZone(@PathVariable String zone) {
        return ApiResponse.success(shiftHandoverService.getShiftsByZone(zone));
    }

    /** 交班流水：全部已交班班次 */
    @GetMapping("/logs")
    public ApiResponse<List<ShiftHandoverResponse>> getHandoverLogs() {
        return ApiResponse.success(shiftHandoverService.getHandoverLogs());
    }

    /** 勾选/取消一项必检（已交班的班拒绝） */
    @PostMapping("/check")
    public ApiResponse<ShiftHandoverResponse> updateCheck(@Valid @RequestBody ShiftCheckRequest request) {
        return ApiResponse.success("点检已保存", shiftHandoverService.updateCheck(request));
    }

    /** 登记/修改铅封号（不允许清空，已交班的班拒绝） */
    @PostMapping("/seal")
    public ApiResponse<ShiftHandoverResponse> updateSeal(@Valid @RequestBody ShiftSealRequest request) {
        return ApiResponse.success("铅封号已保存", shiftHandoverService.updateSealNo(request));
    }

    /** 交班：勾齐 + 铅封号非空 + 接班人非空且不同于当班人，否则拒绝 */
    @PostMapping("/handover")
    public ApiResponse<ShiftHandoverResponse> submitHandover(
            @Valid @RequestBody ShiftHandoverSubmitRequest request) {
        return ApiResponse.success("交班成功，本班已冻结", shiftHandoverService.submitHandover(request));
    }
}
