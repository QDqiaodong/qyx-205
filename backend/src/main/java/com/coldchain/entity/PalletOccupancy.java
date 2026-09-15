package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 托盘落架占用流水：一条记录对应一次“落架→下架”的完整生命周期。
 * 落架时插入 status=1（在架），下架时把同一条记录更新为 status=2（已下架）并回填下架信息，
 * 历史记录永久保留，用于台账、剩余承重核对与追溯。
 */
@Entity
@Table(
        name = "pallet_occupancy",
        indexes = {
                @Index(name = "idx_pallet_shelf_id", columnList = "shelf_id"),
                @Index(name = "idx_pallet_pallet_no", columnList = "pallet_no"),
                @Index(name = "idx_pallet_status", columnList = "status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pallet_no", nullable = false, length = 50)
    private String palletNo;

    @Column(name = "shelf_id", nullable = false)
    private Long shelfId;

    @Column(name = "shelf_no", nullable = false, length = 50)
    private String shelfNo;

    @Column(name = "gross_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossWeight;

    /** 状态：1在架 2已下架 */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(name = "landed_at", nullable = false)
    private LocalDateTime landedAt;

    @Column(name = "landed_by", length = 50)
    private String landedBy;

    @Column(name = "land_remark", length = 255)
    private String landRemark;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "removed_by", length = 50)
    private String removedBy;

    @Column(name = "remove_remark", length = 255)
    private String removeRemark;

    @PrePersist
    protected void onCreate() {
        if (landedAt == null) {
            landedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = 1;
        }
    }
}
