package com.coldchain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 托盘落架登记请求：托盘号 + 毛重 + 落在哪一架。
 */
@Data
public class PalletLandRequest {

    @NotNull(message = "货架ID不能为空")
    private Long shelfId;

    @NotBlank(message = "托盘号不能为空")
    private String palletNo;

    @NotNull(message = "毛重不能为空")
    @Positive(message = "毛重必须大于0")
    private BigDecimal grossWeight;

    private String operator;

    private String remark;
}
