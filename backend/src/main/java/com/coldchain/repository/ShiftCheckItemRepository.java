package com.coldchain.repository;

import com.coldchain.entity.ShiftCheckItem;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftCheckItemRepository extends JpaRepository<ShiftCheckItem, Long> {

    /** 一班的全部必检项（按主键即创建顺序返回） */
    List<ShiftCheckItem> findByShiftIdOrderByIdAsc(Long shiftId);

    long countByShiftIdAndChecked(Long shiftId, Integer checked);

    /**
     * 锁一班的全部明细行。点检更新必须在班行锁之后再锁明细，
     * 同一班的两个并发勾选在此串行，不会互相覆盖。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ShiftCheckItem i where i.shiftId = :shiftId order by i.id asc")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    List<ShiftCheckItem> findByShiftIdForUpdate(@Param("shiftId") Long shiftId);
}
