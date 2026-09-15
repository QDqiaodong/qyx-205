package com.coldchain.repository;

import com.coldchain.entity.LocationCode;
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
public interface LocationCodeRepository extends JpaRepository<LocationCode, Long> {

    Optional<LocationCode> findByCode(String code);

    Optional<LocationCode> findByShelfId(Long shelfId);

    List<LocationCode> findByStatus(Integer status);

    List<LocationCode> findByShelfIdIsNull();

    boolean existsByCode(String code);

    boolean existsByShelfId(Long shelfId);

    /**
     * 只查编码主键，用于在持货架锁后判定目标编码是否已存在，不污染持久化上下文。
     */
    @Query("select c.id from LocationCode c where c.code = :code")
    Optional<Long> findIdByCode(@Param("code") String code);

    /**
     * 按主键升序一次性对多笔编码行加悲观写锁（重分配需同时锁旧码/新码两行）。
     * InnoDB 沿主键索引顺序加锁，所有事务一律按 id 升序取编码锁，锁序全局一致，
     * 两个互相换码的重分配事务也不会互相等待成环。
     * 返回的实体即为持锁后的最新状态，调用方直接据此校验，无需再行查询。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LocationCode c where c.id in :ids order by c.id asc")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    List<LocationCode> findByIdsForUpdateOrderByIdAsc(@Param("ids") List<Long> ids);
}
