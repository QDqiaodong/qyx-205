package com.coldchain.repository;

import com.coldchain.entity.PalletOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PalletOccupancyRepository extends JpaRepository<PalletOccupancy, Long> {

    /** 某货架当前在架的全部托盘（落架时间正序） */
    List<PalletOccupancy> findByShelfIdAndStatusOrderByLandedAtAsc(Long shelfId, Integer status);

    /** 某托盘号当前在架的记录（流水表内也不应出现两条在架，由 pallet_active 主键约束兜底） */
    Optional<PalletOccupancy> findByPalletNoAndStatus(String palletNo, Integer status);

    /** 全量在架记录，下架后不再出现 */
    List<PalletOccupancy> findByStatusOrderByLandedAtDesc(Integer status);

    /** 托盘号的全部落架/下架历史（最新在前） */
    List<PalletOccupancy> findByPalletNoOrderByLandedAtDesc(String palletNo);

    /** 某货架全部历史（最新在前） */
    List<PalletOccupancy> findByShelfIdOrderByLandedAtDesc(Long shelfId);

    boolean existsByShelfIdAndStatus(Long shelfId, Integer status);

    /**
     * 按货架分组汇总在架毛重。承重核对必须在持锁事务内调用，
     * 且对“当前事务刚插入未落库的托盘”使用持久化上下文内的数据再复核。
     */
    @Query("select o.shelfId as shelfId, coalesce(sum(o.grossWeight), 0) as totalWeight "
            + "from PalletOccupancy o where o.status = 1 and o.shelfId in :shelfIds group by o.shelfId")
    List<ShelfWeightSum> sumActiveWeightByShelfIds(@Param("shelfIds") List<Long> shelfIds);

    @Query("select coalesce(sum(o.grossWeight), 0) from PalletOccupancy o "
            + "where o.status = 1 and o.shelfId = :shelfId")
    java.math.BigDecimal sumActiveWeightByShelfId(@Param("shelfId") Long shelfId);
}
