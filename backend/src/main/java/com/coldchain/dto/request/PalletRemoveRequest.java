package com.coldchain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 托盘下架请求：托盘下架后承重退回该架剩余承重。
 */
@Data
public class PalletRemoveRequest {

    @NotBlank(message = "托盘号不能为空")
    private String palletNo;

    private String operator;

    private String remark;
}
