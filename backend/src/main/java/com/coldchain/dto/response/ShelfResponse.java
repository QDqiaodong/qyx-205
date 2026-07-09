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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
