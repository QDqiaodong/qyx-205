package com.coldchain.service;

import com.coldchain.dto.request.CodeBindRequest;
import com.coldchain.dto.request.CodeReassignRequest;
import com.coldchain.dto.request.ShelfCreateRequest;
import com.coldchain.dto.response.CodeMappingResponse;
import com.coldchain.dto.response.ShelfResponse;
import com.coldchain.dto.response.ZoneTreeResponse;
import com.coldchain.entity.CodeChangeLog;

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
}
