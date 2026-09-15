package com.coldchain.repository;

import com.coldchain.entity.Shelf;
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
public interface ShelfRepository extends JpaRepository<Shelf, Long> {

    Optional<Shelf> findByShelfNo(String shelfNo);

    List<Shelf> findByZone(String zone);

    List<Shelf> findByStatus(Integer status);

    List<Shelf> findByZoneAndStatus(String zone, Integer status);

    boolean existsByShelfNo(String shelfNo);

    List<Shelf> findAllByOrderByZoneAscShelfNoAsc();

    /**
     * 行级悲观写锁（MySQL: SELECT ... FOR UPDATE）。
     * 落架/下架/解绑/重分配/删除货架等需要变更“该货架占用状态”的事务，
     * 必须先通过本方法取到货架并持锁到事务提交，同一货架的这些操作因此被完全串行化，
     * 两笔并发落架不可能同时通过承重校验。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shelf s where s.id = :id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<Shelf> findByIdForUpdate(@Param("id") Long id);
}
