package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shelf")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shelf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shelf_no", nullable = false, unique = true, length = 50)
    private String shelfNo;

    @Column(name = "capacity", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacity;

    @Column(name = "zone", nullable = false, length = 50)
    private String zone;

    @Column(name = "status")
    @Builder.Default
    private Integer status = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
