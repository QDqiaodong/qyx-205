package com.coldchain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CodeBindRequest {

    @NotNull(message = "货架ID不能为空")
    private Long shelfId;

    @NotBlank(message = "货位编码不能为空")
    private String code;

    private String operator;
    
    private String remark;
}
