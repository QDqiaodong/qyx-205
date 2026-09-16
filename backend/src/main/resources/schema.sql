CREATE TABLE IF NOT EXISTS shelf (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '货架ID',
    shelf_no VARCHAR(50) NOT NULL UNIQUE COMMENT '货架编号',
    capacity DECIMAL(10,2) NOT NULL COMMENT '承重规格(kg)',
    zone VARCHAR(50) NOT NULL COMMENT '所属库区',
    status TINYINT DEFAULT 1 COMMENT '状态：1可用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_shelf_no (shelf_no),
    INDEX idx_zone (zone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货架表';

CREATE TABLE IF NOT EXISTS location_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '编码ID',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '货位编码',
    shelf_id BIGINT UNIQUE COMMENT '关联货架ID',
    status TINYINT DEFAULT 1 COMMENT '状态：1未绑定 2已绑定 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_code (code),
    INDEX idx_shelf_id (shelf_id),
    FOREIGN KEY (shelf_id) REFERENCES shelf(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货位编码表';

CREATE TABLE IF NOT EXISTS code_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    shelf_id BIGINT NOT NULL COMMENT '货架ID',
    shelf_no VARCHAR(50) NOT NULL COMMENT '货架编号',
    old_code VARCHAR(50) COMMENT '旧货位编码',
    new_code VARCHAR(50) COMMENT '新货位编码',
    operation_type TINYINT NOT NULL COMMENT '操作类型：1绑定 2解绑 3重分配',
    operator VARCHAR(50) COMMENT '操作人',
    remark VARCHAR(255) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_shelf_id (shelf_id),
    INDEX idx_created_at (created_at),
    INDEX idx_operation_type (operation_type),
    FOREIGN KEY (shelf_id) REFERENCES shelf(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编码变更记录表';

-- 托盘落架占用流水：一次落架一条，在架=1、已下架=2，永久保留用于台账与追溯
CREATE TABLE IF NOT EXISTS pallet_occupancy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '占用流水ID',
    pallet_no VARCHAR(50) NOT NULL COMMENT '托盘号',
    shelf_id BIGINT NOT NULL COMMENT '落架货架ID',
    shelf_no VARCHAR(50) NOT NULL COMMENT '落架货架编号（冗余快照）',
    gross_weight DECIMAL(10,2) NOT NULL COMMENT '毛重(kg)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1在架 2已下架',
    landed_at DATETIME NOT NULL COMMENT '落架时间',
    landed_by VARCHAR(50) COMMENT '落架操作人',
    land_remark VARCHAR(255) COMMENT '落架备注',
    removed_at DATETIME COMMENT '下架时间',
    removed_by VARCHAR(50) COMMENT '下架操作人',
    remove_remark VARCHAR(255) COMMENT '下架备注',
    INDEX idx_pallet_shelf_id (shelf_id),
    INDEX idx_pallet_pallet_no (pallet_no),
    INDEX idx_pallet_status (status),
    INDEX idx_pallet_landed_at (landed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='托盘落架占用流水表';

-- 在架托盘登记：仅保留当前在架托盘，主键=托盘号，数据库层保证同一托盘全局只能在架一次
CREATE TABLE IF NOT EXISTS pallet_active (
    pallet_no VARCHAR(50) PRIMARY KEY COMMENT '托盘号（在架全局唯一）',
    occupancy_id BIGINT NOT NULL COMMENT '对应落架流水ID（逻辑关联，不外键约束）',
    shelf_id BIGINT NOT NULL COMMENT '当前所在货架ID',
    shelf_no VARCHAR(50) NOT NULL COMMENT '当前所在货架编号',
    landed_at DATETIME NOT NULL COMMENT '落架时间',
    INDEX idx_active_shelf_id (shelf_id),
    INDEX idx_active_occupancy_id (occupancy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在架托盘登记表';

-- ===================== 库区交接班 =====================

-- 库区开班串行锁：每个库区一行，开班事务先对本行 SELECT ... FOR UPDATE，
-- 保证同一库区的并发开班完全串行：上一班没交完，后来的新班必然被拦住
CREATE TABLE IF NOT EXISTS shift_zone_lock (
    zone VARCHAR(50) PRIMARY KEY COMMENT '库区名称（每库区一行）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库区开班串行锁表';

-- 交接班主表：一班一条，按库区串行；status 1进行中 2已交班（已交班整班冻结）
CREATE TABLE IF NOT EXISTS shift_handover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '班次ID（点检/流水/反查三处对齐同一班）',
    zone VARCHAR(50) NOT NULL COMMENT '库区',
    shift_type VARCHAR(10) NOT NULL COMMENT '班次：白班/夜班',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1进行中 2已交班（冻结）',
    outgoing_name VARCHAR(50) NOT NULL COMMENT '当班人（开班人/交班责任人）',
    incoming_name VARCHAR(50) COMMENT '接班人（交班时必填，不得与当班人同名）',
    seal_no VARCHAR(100) COMMENT '铅封号（交班前必填，交班后不得清空）',
    handover_note VARCHAR(500) COMMENT '交班说明（口头交代落纸面）',
    opened_at DATETIME NOT NULL COMMENT '开班时间',
    handed_at DATETIME COMMENT '交班成功时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_shift_zone (zone),
    INDEX idx_shift_status (status),
    INDEX idx_shift_zone_status (zone, status),
    INDEX idx_shift_handed_at (handed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库区交接班主表';

-- 数据库层兜底：同一库区最多只能有一条进行中(status=1)的班。
-- 已交班行的 active_zone 为 NULL，MySQL 唯一索引中多个 NULL 不冲突。
CREATE UNIQUE INDEX uk_shift_zone_active
    ON shift_handover ((CASE WHEN status = 1 THEN zone ELSE NULL END));

-- 交接班必检项明细：一班三条（外观/门帘/铅封），checked=1 才算勾齐
CREATE TABLE IF NOT EXISTS shift_check_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    shift_id BIGINT NOT NULL COMMENT '所属班次ID',
    item_code VARCHAR(32) NOT NULL COMMENT '必检项编码：APPEARANCE/DOOR_CURTAIN/SEAL',
    item_name VARCHAR(100) NOT NULL COMMENT '必检项名称（快照）',
    checked TINYINT NOT NULL DEFAULT 0 COMMENT '是否勾选：0未勾 1已勾',
    checked_at DATETIME COMMENT '最后勾选时间',
    remark VARCHAR(255) COMMENT '点检备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_check_shift_id (shift_id),
    CONSTRAINT fk_check_shift FOREIGN KEY (shift_id)
        REFERENCES shift_handover(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交接班必检项明细表';
