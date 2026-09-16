package com.coldchain.repository;

import com.coldchain.entity.ShiftHandover;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftHandoverRepository extends JpaRepository<ShiftHandover, Long> {

    /** 某库区进行中的班（一个库区最多一条，由部分唯一索引 uk_zone_active 兜底） */
    Optional<ShiftHandover> findByZoneAndStatus(String zone, Integer status);

    /** 某库区全部班次，最新开班在前 */
    List<ShiftHandover> findByZoneOrderByOpenedAtDescIdDesc(String zone);

    /** 交班流水（全部已交班班次），交班时间倒序 */
    List<ShiftHandover> findByStatusOrderByHandedAtDescIdDesc(Integer status);

    /** 全部班次，开班时间倒序（总览用） */
    List<ShiftHandover> findAllByOrderByOpenedAtDescIdDesc();

    long countByZoneAndStatus(String zone, Integer status);

    /**
     * 按班主键加悲观写锁。
     * 点检勾选/改铅封号/交班都先锁班行：这些针对同一班的并发改动被完全串行化；
     * 锁内复核 status，已交班的班直接拒绝，保证“交出去后不许再改”。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from ShiftHandover h where h.id = :id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<ShiftHandover> findByIdForUpdate(@Param("id") Long id);
}
