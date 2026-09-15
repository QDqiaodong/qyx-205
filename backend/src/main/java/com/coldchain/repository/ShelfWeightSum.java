package com.coldchain.repository;

import java.math.BigDecimal;

/**
 * 在架毛重聚合行。
 */
public interface ShelfWeightSum {
    Long getShelfId();
    BigDecimal getTotalWeight();
}
