package com.coldchain.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CodeMappingResponse {

    private Long shelfId;
    private String shelfNo;
    private BigDecimal capacity;
    private String zone;
    private String locationCode;
    private LocalDateTime bindTime;
}
