package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交接班必检项明细（一班多条，与班次同事务原子写入）。
 * 必检项固定为：库区外观完好、保温门帘完好、铅封已点并登记铅封号。
 * checked=1 才算勾齐；漏勾任何一项都交不出去。
 * 已交班的班，明细永久冻结，没有任何接口可以再改勾选状态。
 */
@Entity
@Table(name = "shift_check_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftCheckItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属班次ID */
    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    /** 必检项编码：APPEARANCE 外观 / DOOR_CURTAIN 门帘 / SEAL 铅封 */
    @Column(name = "item_code", nullable = false, length = 32)
    private String itemCode;

    /** 必检项名称（冗余快照，页面改名不影响历史班台账） */
    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    /** 是否勾选：0未勾 1已勾 */
    @Column(name = "checked", nullable = false)
    @Builder.Default
    private Integer checked = 0;

    /** 勾选/取消时间（最后一次） */
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    /** 点检备注 */
    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
