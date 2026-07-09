package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_change_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shelf_id", nullable = false)
    private Long shelfId;

    @Column(name = "shelf_no", nullable = false, length = 50)
    private String shelfNo;

    @Column(name = "old_code", length = 50)
    private String oldCode;

    @Column(name = "new_code", length = 50)
    private String newCode;

    @Column(name = "operation_type", nullable = false)
    private Integer operationType;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
