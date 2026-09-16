package com.coldchain.dto.response;

import lombok.Data;

/**
 * 必检项明细响应。
 */
@Data
public class ShiftCheckItemResponse {

    private Long id;
    private Long shiftId;

    /** APPEARANCE 外观 / DOOR_CURTAIN 门帘 / SEAL 铅封 */
    private String itemCode;
    private String itemName;

    /** 0未勾 1已勾 */
    private Integer checked;
    private String checkedAt;
    private String remark;
}
