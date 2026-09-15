package com.coldchain.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单条在架托盘视图。
 */
@Data
public class PalletOccupancyResponse {

    private Long id;
    private String palletNo;
    private Long shelfId;
    private String shelfNo;
    private BigDecimal grossWeight;
    private Integer status;
    private LocalDateTime landedAt;
    private String landedBy;
    private String landRemark;
    private LocalDateTime removedAt;
    private String removedBy;
    private String removeRemark;
}
