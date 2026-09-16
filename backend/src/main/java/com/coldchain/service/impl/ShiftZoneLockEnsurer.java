package com.coldchain.service.impl;

import com.coldchain.entity.ShiftZoneLock;
import com.coldchain.repository.ShiftZoneLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库区开班串行锁助手。
 *
 * 锁行（每库区一行）是开班串行化的锚点，但某个库区第一次开班时这一行还不存在。
 * “确保锁行存在并对其加锁”放在独立短事务（REQUIRES_NEW）里：
 *  - 首次：插入锁行，独立事务立即提交（行锁随之释放），行从此对所有开班事务可见；
 *  - 并发首开撞主键：只回滚这个内部小事务，不污染外层开班事务，再查一次即可拿到已提交的行。
 * 外层开班事务随后再对这把“已提交的锁行”做 FOR UPDATE，同一库区的开班由此完全串行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftZoneLockEnsurer {

    private final ShiftZoneLockRepository zoneLockRepository;

    /** 在独立事务内确保锁行已提交可见（撞主键说明并发方已建好，忽略）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureLockRowExists(String zone) {
        try {
            zoneLockRepository.saveAndFlush(ShiftZoneLock.builder().zone(zone).build());
        } catch (DataIntegrityViolationException alreadyExists) {
            log.debug("库区 {} 开班锁行已由并发事务创建，复用该行", zone);
        }
    }

    /** 在外层开班事务里对锁行加悲观写锁，直到该开班事务提交才释放。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockExistingRow(String zone) {
        ShiftZoneLock lockRow = zoneLockRepository.findByZoneForUpdate(zone);
        if (lockRow == null) {
            // 理论上不可达：ensureLockRowExists 已确保行已提交
            throw new IllegalStateException("库区 " + zone + " 开班锁行不存在");
        }
    }
}
