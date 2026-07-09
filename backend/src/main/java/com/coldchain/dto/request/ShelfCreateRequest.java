package com.coldchain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShelfCreateRequest {

    @NotBlank(message = "货架编号不能为空")
    private String shelfNo;

    @NotNull(message = "承重规格不能为空")
    @Positive(message = "承重规格必须大于0")
    private BigDecimal capacity;

    @NotBlank(message = "所属库区不能为空")
    private String zone;
}
