package com.coldchain.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 货架占用情况：额定承重、在架合计、剩余承重、在架托数及托盘明细。
 * remainingCapacity = capacity - usedCapacity，始终以数据库在架流水合计为准，
 * 关闭页面重开由同一套数据重算，保证对得上。
 */
@Data
public class ShelfOccupancyResponse {

    private Long shelfId;
    private String shelfNo;
    private String zone;
    private BigDecimal capacity;
    private String locationCode;
    private Integer palletCount;
    private BigDecimal usedCapacity;
    private BigDecimal remainingCapacity;
    private List<PalletOccupancyResponse> pallets;
}
