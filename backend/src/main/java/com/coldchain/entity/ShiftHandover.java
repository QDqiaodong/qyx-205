package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库区交接班（一班一条，按库区串行）。
 * status=1 进行中（本班当班人可继续点检、交班）；status=2 已交班（整班冻结，不可再改）。
 * 同一库区只允许存在一条进行中的班：由 shift_zone_lock 行锁串行化开班事务，
 * 并由 (zone, status) 部分唯一索引在数据库层兜底。
 */
@Entity
@Table(name = "shift_handover")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 库区名称（冗余快照，开班后即不允许改动） */
    @Column(name = "zone", nullable = false, length = 50)
    private String zone;

    /** 班次标识：白班/夜班 */
    @Column(name = "shift_type", nullable = false, length = 10)
    private String shiftType;

    /**
     * 状态：1进行中 2已交班（冻结）。
     * 注意：不加 (zone, status) 普通唯一约束——同库区允许多条已交班历史；
     * “同一库区仅一条进行中”由 schema.sql 的函数唯一索引 uk_shift_zone_active 保证。
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;

    /** 当班人（开班人，本班的点检与交班责任人） */
    @Column(name = "outgoing_name", nullable = false, length = 50)
    private String outgoingName;

    /** 接班人，交班前为空；交班时必填且不得与当班人同名 */
    @Column(name = "incoming_name", length = 50)
    private String incomingName;

    /** 铅封号，交班前必须填写；交班后永久留痕、不得清空 */
    @Column(name = "seal_no", length = 100)
    private String sealNo;

    /** 交班说明（口头交代落到纸面） */
    @Column(name = "handover_note", length = 500)
    private String handoverNote;

    /** 开班时间 */
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    /** 交班成功时间 */
    @Column(name = "handed_at")
    private LocalDateTime handedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (openedAt == null) {
            openedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
