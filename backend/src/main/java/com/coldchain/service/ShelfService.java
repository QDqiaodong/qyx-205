package com.coldchain.service;

import com.coldchain.dto.request.CodeBindRequest;
import com.coldchain.dto.request.CodeReassignRequest;
import com.coldchain.dto.request.PalletLandRequest;
import com.coldchain.dto.request.PalletRemoveRequest;
import com.coldchain.dto.request.ShelfCreateRequest;
import com.coldchain.dto.response.CodeMappingResponse;
import com.coldchain.dto.response.PalletOccupancyResponse;
import com.coldchain.dto.response.ShelfOccupancyResponse;
import com.coldchain.dto.response.ShelfResponse;
import com.coldchain.dto.response.ZoneTreeResponse;
import com.coldchain.entity.CodeChangeLog;
import com.coldchain.entity.PalletOccupancy;

import java.util.List;

public interface ShelfService {

    ShelfResponse createShelf(ShelfCreateRequest request);

    ShelfResponse getShelfById(Long id);

    ShelfResponse getShelfByNo(String shelfNo);

    List<ShelfResponse> getAllShelves();

    List<ShelfResponse> getShelvesByZone(String zone);

    void deleteShelf(Long id);

    ShelfResponse bindCode(CodeBindRequest request);

    ShelfResponse unbindCode(Long shelfId, String operator, String remark);

    ShelfResponse reassignCode(CodeReassignRequest request);

    ShelfResponse searchByCode(String code);

    List<ZoneTreeResponse> getZoneTree();

    List<CodeMappingResponse> getAllCodeMappings();

    List<CodeChangeLog> getChangeLogsByShelfId(Long shelfId);

    List<CodeChangeLog> getAllChangeLogs();

    String exportCodeMapping();

    // ========== 托盘落架占用 ==========

    /** 托盘落架登记：校验已绑编码、托盘未在架、在架合计+本托不超额定承重；返回更新后的货架占用 */
    ShelfOccupancyResponse landPallet(PalletLandRequest request);

    /** 托盘下架：承重退回，返回更新后的货架占用 */
    ShelfOccupancyResponse removePallet(PalletRemoveRequest request);

    /** 单个货架的占用详情（含在架托盘明细） */
    ShelfOccupancyResponse getShelfOccupancy(Long shelfId);

    /** 全部在架托盘列表（支持按货架编号过滤） */
    List<PalletOccupancyResponse> getActivePallets(String shelfNo);

    /** 托盘号落架/下架历史 */
    List<PalletOccupancy> getPalletHistory(String palletNo);
}
