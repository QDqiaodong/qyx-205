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
