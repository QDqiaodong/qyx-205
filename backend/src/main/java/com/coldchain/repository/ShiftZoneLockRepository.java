package com.coldchain.repository;

import com.coldchain.entity.ShiftZoneLock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftZoneLockRepository extends JpaRepository<ShiftZoneLock, String> {

    /**
     * 库区开班行锁（SELECT ... FOR UPDATE）。
     * 开班事务必须先拿到本库区这一行的写锁，再检查/插入进行中的班，
     * 从而保证“同一库区上一班没交完，后开的新班必然被拦住”，先开的班保持原样。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ShiftZoneLock l where l.zone = :zone")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    ShiftZoneLock findByZoneForUpdate(@Param("zone") String zone);
}
