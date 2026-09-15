package com.coldchain.controller;

import com.coldchain.dto.request.CodeBindRequest;
import com.coldchain.dto.request.CodeReassignRequest;
import com.coldchain.dto.request.PalletLandRequest;
import com.coldchain.dto.request.PalletRemoveRequest;
import com.coldchain.dto.request.ShelfCreateRequest;
import com.coldchain.dto.response.ApiResponse;
import com.coldchain.dto.response.CodeMappingResponse;
import com.coldchain.dto.response.PalletOccupancyResponse;
import com.coldchain.dto.response.ShelfOccupancyResponse;
import com.coldchain.dto.response.ShelfResponse;
import com.coldchain.dto.response.ZoneTreeResponse;
import com.coldchain.entity.CodeChangeLog;
import com.coldchain.entity.PalletOccupancy;
import com.coldchain.service.ShelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/shelf")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ShelfController {

    private final ShelfService shelfService;

    @PostMapping
    public ApiResponse<ShelfResponse> createShelf(@Valid @RequestBody ShelfCreateRequest request) {
        return ApiResponse.success(shelfService.createShelf(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShelfResponse> getShelfById(@PathVariable Long id) {
        return ApiResponse.success(shelfService.getShelfById(id));
    }

    @GetMapping("/search/by-no")
    public ApiResponse<ShelfResponse> getShelfByNo(@RequestParam String shelfNo) {
        return ApiResponse.success(shelfService.getShelfByNo(shelfNo));
    }

    @GetMapping
    public ApiResponse<List<ShelfResponse>> getAllShelves() {
        return ApiResponse.success(shelfService.getAllShelves());
    }

    @GetMapping("/zone/{zone}")
    public ApiResponse<List<ShelfResponse>> getShelvesByZone(@PathVariable String zone) {
        return ApiResponse.success(shelfService.getShelvesByZone(zone));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteShelf(@PathVariable Long id) {
        shelfService.deleteShelf(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/bind-code")
    public ApiResponse<ShelfResponse> bindCode(@Valid @RequestBody CodeBindRequest request) {
        return ApiResponse.success("绑定成功", shelfService.bindCode(request));
    }

    @PostMapping("/unbind-code/{shelfId}")
    public ApiResponse<ShelfResponse> unbindCode(@PathVariable Long shelfId,
                                                  @RequestParam(required = false) String operator,
                                                  @RequestParam(required = false) String remark) {
        return ApiResponse.success("解绑成功", shelfService.unbindCode(shelfId, operator, remark));
    }

    @PostMapping("/reassign-code")
    public ApiResponse<ShelfResponse> reassignCode(@Valid @RequestBody CodeReassignRequest request) {
        return ApiResponse.success("重分配成功", shelfService.reassignCode(request));
    }

    @GetMapping("/search/by-code")
    public ApiResponse<ShelfResponse> searchByCode(@RequestParam String code) {
        return ApiResponse.success(shelfService.searchByCode(code));
    }

    @GetMapping("/zone-tree")
    public ApiResponse<List<ZoneTreeResponse>> getZoneTree() {
        return ApiResponse.success(shelfService.getZoneTree());
    }

    @GetMapping("/code-mappings")
    public ApiResponse<List<CodeMappingResponse>> getAllCodeMappings() {
        return ApiResponse.success(shelfService.getAllCodeMappings());
    }

    @GetMapping("/change-logs/{shelfId}")
    public ApiResponse<List<CodeChangeLog>> getChangeLogsByShelfId(@PathVariable Long shelfId) {
        return ApiResponse.success(shelfService.getChangeLogsByShelfId(shelfId));
    }

    @GetMapping("/change-logs")
    public ApiResponse<List<CodeChangeLog>> getAllChangeLogs() {
        return ApiResponse.success(shelfService.getAllChangeLogs());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCodeMapping() {
        String csv = shelfService.exportCodeMapping();

        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "shelf_code_mapping.csv");
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }

    // ===================== 托盘落架占用 =====================

    /** 托盘落架登记：托盘号、毛重、落在哪一架 */
    @PostMapping("/pallets/land")
    public ApiResponse<ShelfOccupancyResponse> landPallet(@Valid @RequestBody PalletLandRequest request) {
        return ApiResponse.success("落架成功", shelfService.landPallet(request));
    }

    /** 托盘下架：承重退回 */
    @PostMapping("/pallets/remove")
    public ApiResponse<ShelfOccupancyResponse> removePallet(@Valid @RequestBody PalletRemoveRequest request) {
        return ApiResponse.success("下架成功，承重已退回", shelfService.removePallet(request));
    }

    /** 单架占用详情（额定/在架合计/剩余/托盘明细） */
    @GetMapping("/{shelfId}/occupancy")
    public ApiResponse<ShelfOccupancyResponse> getShelfOccupancy(@PathVariable Long shelfId) {
        return ApiResponse.success(shelfService.getShelfOccupancy(shelfId));
    }

    /** 全部在架托盘，可按货架编号过滤 */
    @GetMapping("/pallets/active")
    public ApiResponse<List<PalletOccupancyResponse>> getActivePallets(
            @RequestParam(required = false) String shelfNo) {
        return ApiResponse.success(shelfService.getActivePallets(shelfNo));
    }

    /** 托盘号落架/下架历史 */
    @GetMapping("/pallets/history")
    public ApiResponse<List<PalletOccupancy>> getPalletHistory(@RequestParam String palletNo) {
        return ApiResponse.success(shelfService.getPalletHistory(palletNo));
    }
}
