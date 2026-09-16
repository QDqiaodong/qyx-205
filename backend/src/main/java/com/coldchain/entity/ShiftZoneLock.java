package com.coldchain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库区开班串行锁：每个库区一行。
 * 开班事务先对本库区这一行做 SELECT ... FOR UPDATE，拿到行锁后才检查是否存在未交班的班。
 * 两笔并发开班在同一行锁上排队：后到者持锁后必然看到先到者已提交的进行中班级 → 被拦住，
 * 先开的那一班原样不动。锁行只负责串行化，不承载业务状态，因此永不删除。
 */
@Entity
@Table(name = "shift_zone_lock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftZoneLock {

    /** 主键即库区名称，天然每库区一行 */
    @Id
    @Column(name = "zone", length = 50)
    private String zone;
}
