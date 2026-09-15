package com.coldchain.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShelfResponse {

    private Long id;
    private String shelfNo;
    private BigDecimal capacity;
    private String zone;
    private Integer status;
    private String locationCode;

    /** 在架托数 */
    private Integer palletCount;
    /** 在架毛重合计(kg) */
    private BigDecimal usedCapacity;
    /** 剩余承重(kg) = 额定承重 - 在架毛重合计 */
    private BigDecimal remainingCapacity;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
