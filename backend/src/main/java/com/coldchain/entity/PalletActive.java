package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 在架托盘登记表：仅保存当前仍在架（未下架）的托盘，主键即托盘号。
 * 作用：
 * 1. 数据库层面保证一个托盘号同一时刻只能在一架上在架一次（一对一在架关系，跨货架全局唯一）；
 * 2. 与 pallet_occupancy 流水表配合，落架插入、下架删除，同事务提交。
 * 货架→托盘是一对多（同架可有多托），托盘→在架记录是一对一，二者不能混为货位编码绑定。
 */
@Entity
@Table(name = "pallet_active")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletActive {

    /** 托盘号，主键：全局在架唯一 */
    @Id
    @Column(name = "pallet_no", length = 50)
    private String palletNo;

    @Column(name = "occupancy_id", nullable = false)
    private Long occupancyId;

    @Column(name = "shelf_id", nullable = false)
    private Long shelfId;

    @Column(name = "shelf_no", nullable = false, length = 50)
    private String shelfNo;

    @Column(name = "landed_at", nullable = false)
    private LocalDateTime landedAt;
}
