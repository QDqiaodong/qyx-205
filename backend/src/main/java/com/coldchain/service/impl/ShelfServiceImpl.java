package com.coldchain.service.impl;

import com.coldchain.dto.request.CodeBindRequest;
import com.coldchain.dto.request.CodeReassignRequest;
import com.coldchain.dto.request.PalletLandRequest;
import com.coldchain.dto.request.PalletRemoveRequest;
import com.coldchain.dto.request.ShelfCreateRequest;
import com.coldchain.dto.response.CodeMappingResponse;
import com.coldchain.dto.response.PalletOccupancyResponse;
import com.coldchain.dto.response.ShelfOccupancyResponse;
import com.coldchain.dto.response.ShelfResponse;
import com.coldchain.dto.response.ZoneTreeResponse;
import com.coldchain.entity.CodeChangeLog;
import com.coldchain.entity.LocationCode;
import com.coldchain.entity.PalletActive;
import com.coldchain.entity.PalletOccupancy;
import com.coldchain.entity.Shelf;
import com.coldchain.exception.BusinessException;
import com.coldchain.repository.CodeChangeLogRepository;
import com.coldchain.repository.LocationCodeRepository;
import com.coldchain.repository.PalletActiveRepository;
import com.coldchain.repository.PalletOccupancyRepository;
import com.coldchain.repository.ShelfRepository;
import com.coldchain.repository.ShelfWeightSum;
import com.coldchain.service.ShelfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShelfServiceImpl implements ShelfService {

    private final ShelfRepository shelfRepository;
    private final LocationCodeRepository locationCodeRepository;
    private final CodeChangeLogRepository codeChangeLogRepository;
    private final PalletOccupancyRepository palletOccupancyRepository;
    private final PalletActiveRepository palletActiveRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "shelf:capacity:";
    private static final int REDIS_TTL_HOURS = 24;
    private static final int OPERATION_BIND = 1;
    private static final int OPERATION_UNBIND = 2;
    private static final int OPERATION_REASSIGN = 3;

    /** 托盘占用状态 */
    private static final int PALLET_ON_RACK = 1;
    private static final int PALLET_REMOVED = 2;

    @Override
    @Transactional
    public ShelfResponse createShelf(ShelfCreateRequest request) {
        if (shelfRepository.existsByShelfNo(request.getShelfNo())) {
            throw new BusinessException("货架编号已存在");
        }

        Shelf shelf = Shelf.builder()
                .shelfNo(request.getShelfNo())
                .capacity(normalizeWeight(request.getCapacity()))
                .zone(request.getZone())
                .status(1)
                .build();

        shelf = shelfRepository.save(shelf);

        redisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + shelf.getId(),
                shelf.getCapacity(),
                REDIS_TTL_HOURS,
                TimeUnit.HOURS
        );

        return buildShelfResponse(shelf, null, noOccupancy());
    }

    @Override
    public ShelfResponse getShelfById(Long id) {
        Shelf shelf = shelfRepository.findById(id)
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode locationCode = locationCodeRepository.findByShelfId(id).orElse(null);
        return buildShelfResponse(shelf, locationCode, loadOccupancy(id));
    }

    @Override
    public ShelfResponse getShelfByNo(String shelfNo) {
        Shelf shelf = shelfRepository.findByShelfNo(shelfNo)
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode locationCode = locationCodeRepository.findByShelfId(shelf.getId()).orElse(null);
        return buildShelfResponse(shelf, locationCode, loadOccupancy(shelf.getId()));
    }

    @Override
    public List<ShelfResponse> getAllShelves() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByZoneAscShelfNoAsc();
        return enrichShelves(shelves);
    }

    @Override
    public List<ShelfResponse> getShelvesByZone(String zone) {
        List<Shelf> shelves = shelfRepository.findByZone(zone);
        return enrichShelves(shelves);
    }

    @Override
    @Transactional
    public void deleteShelf(Long id) {
        // 锁序与绑定/解绑/重分配一致：先锁货架行，再锁编码行
        Shelf shelf = shelfRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("货架不存在"));

        // 持货架行锁，与落架/下架互斥；有在架托盘时禁止删除，避免托盘变孤儿、承重对不上
        long activeCount = palletActiveRepository.countByShelfId(id);
        if (activeCount > 0) {
            throw new BusinessException("货架上还有 " + activeCount + " 托在架托盘，请先全部下架并退回承重后再删除");
        }

        // 普通读在拿到货架锁之后才发生：能改动本架绑定的事务都必须先拿本架锁，此刻已全部提交，可见
        LocationCode locationCode = locationCodeRepository.findByShelfId(id).orElse(null);
        if (locationCode != null) {
            // 编码行仍需加锁：另一货架的重分配事务只需“对方货架锁+本编码锁”就能把它迁走，
            // 锁内复核归属，若已被迁到别架则不得清空，避免误解绑别人的新绑定
            List<LocationCode> locked = locationCodeRepository
                    .findByIdsForUpdateOrderByIdAsc(List.of(locationCode.getId()));
            locationCode = locked.stream()
                    .filter(c -> id.equals(c.getShelfId()))
                    .findFirst()
                    .orElse(null);
            if (locationCode != null) {
                locationCode.setShelfId(null);
                locationCode.setStatus(1);
                locationCodeRepository.save(locationCode);

                saveChangeLog(shelf.getId(), shelf.getShelfNo(), locationCode.getCode(), null,
                        OPERATION_UNBIND, "system", "删除货架自动解绑");
            }
        }

        shelfRepository.delete(shelf);
        redisTemplate.delete(REDIS_KEY_PREFIX + id);
    }

    @Override
    @Transactional
    public ShelfResponse bindCode(CodeBindRequest request) {
        String code = request.getCode();

        // 1) 先锁目标货架行：同一货架的绑定/解绑/重分配/落架全部在此串行。
        //    本事务此前没有任何普通查询，拿到货架锁后的第一次普通读才建立一致性快照，
        //    因此必然看到所有先前已提交的本架改码事务。
        Shelf shelf = shelfRepository.findByIdForUpdate(request.getShelfId())
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode currentOnShelf = locationCodeRepository.findByShelfId(shelf.getId()).orElse(null);
        if (currentOnShelf != null) {
            throw new BusinessException("货架已绑定货位编码: " + currentOnShelf.getCode());
        }

        // 2) 再锁目标编码行（锁序固定为“货架 → 编码(id 升序)”，无死锁环）。
        //    两人同时把同一编码绑到两架空架：两人各持各的货架锁，在编码行上排队，
        //    先到者提交后，后到者的锁定读拿到的是锁内最新行，shelf_id 已被占用 → 必然失败。
        Long targetCodeId = locationCodeRepository.findIdByCode(code).orElse(null);
        LocationCode locationCode;
        if (targetCodeId != null) {
            LocationCode target = locationCodeRepository
                    .findByIdsForUpdateOrderByIdAsc(List.of(targetCodeId))
                    .get(0);
            if (target.getShelfId() != null) {
                // 并发双绑里后提交的一方就在这里出局：不写绑定关系、不写流水，先到那架原样不动
                throw new BusinessException("货位编码已被其他货架绑定");
            }
            locationCode = target;
        } else {
            // 系统中还没有该编码：直接插入并立即刷库，由 location_code.code 唯一约束裁决。
            // 两笔并发同时新建同一编码时，先到者插入成功；后到者撞唯一约束（这里不预先加
            // gap lock——两边各持一把兼容 gap lock 再插入反而会触发 1213 死锁）。
            // 唯一约束冲突发生后事务已被标记 rollback-only，不能吞掉继续走，
            // 翻译成业务提示让整笔回滚：先到那架的绑定关系与绑定流水保持不变。
            try {
                locationCode = locationCodeRepository.saveAndFlush(LocationCode.builder()
                        .code(code)
                        .status(1)
                        .build());
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(409, "并发操作冲突：货位编码 " + code
                        + " 刚被另一笔操作绑定，本笔绑定失败，原绑定保持不变");
            }
        }

        locationCode.setShelfId(shelf.getId());
        locationCode.setStatus(2);
        locationCodeRepository.saveAndFlush(locationCode);

        // 3) 绑定关系与流水同一事务原子提交：要么绑定+流水一起可见，要么整笔回滚两处都不动。
        //    导出对照表/按编码反查/变更流水三处读的都是同一份已提交现状，不会再互相对不上。
        saveChangeLog(shelf.getId(), shelf.getShelfNo(), null, code, OPERATION_BIND,
                request.getOperator(), request.getRemark());

        return buildShelfResponse(shelf, locationCode, loadOccupancy(shelf.getId()));
    }

    @Override
    @Transactional
    public ShelfResponse unbindCode(Long shelfId, String operator, String remark) {
        // 先锁货架行，与并发落架及同架改码操作互斥
        Shelf shelf = shelfRepository.findByIdForUpdate(shelfId)
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode locationCode = locationCodeRepository.findByShelfId(shelfId).orElse(null);
        if (locationCode == null) {
            throw new BusinessException("货架未绑定货位编码");
        }

        // 再锁编码行并复核归属：另一货架的并发重分配可能已把该码迁走，那时本架实际已无码
        LocationCode locked = locationCodeRepository
                .findByIdsForUpdateOrderByIdAsc(List.of(locationCode.getId()))
                .get(0);
        if (!shelfId.equals(locked.getShelfId())) {
            throw new BusinessException(409, "并发操作冲突：该编码的绑定状态已被另一笔操作变更，请刷新后重试");
        }

        assertNoActivePallet(shelfId, "解绑货位编码");

        String oldCode = locked.getCode();

        locked.setShelfId(null);
        locked.setStatus(1);
        locationCodeRepository.save(locked);

        saveChangeLog(shelf.getId(), shelf.getShelfNo(), oldCode, null, OPERATION_UNBIND,
                operator, remark);

        return buildShelfResponse(shelf, null, noOccupancy());
    }

    @Override
    @Transactional
    public ShelfResponse reassignCode(CodeReassignRequest request) {
        // 1) 先锁目标货架行：同架的一切改码/落架操作在此串行，持锁后的普通读对本架状态是权威的
        Shelf shelf = shelfRepository.findByIdForUpdate(request.getShelfId())
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode oldCode = locationCodeRepository.findByShelfId(shelf.getId()).orElse(null);

        assertNoActivePallet(shelf.getId(), "重分配货位编码");

        Long newCodeId = locationCodeRepository.findIdByCode(request.getNewCode()).orElse(null);
        if (oldCode != null && oldCode.getId().equals(newCodeId)) {
            throw new BusinessException("新货位编码与当前绑定编码相同，无需重分配");
        }

        // 2) 再按 id 升序锁旧码/新码两行（新码不存在则交给 code 唯一约束裁决并发插入）。
        //    货架→编码单向加锁、编码按 id 升序，两个互换编码的重分配事务也不会互等成环。
        List<Long> codeIds = new ArrayList<>(2);
        if (oldCode != null) {
            codeIds.add(oldCode.getId());
        }
        if (newCodeId != null) {
            codeIds.add(newCodeId);
        }
        Map<Long, LocationCode> lockedMap = codeIds.isEmpty()
                ? Collections.emptyMap()
                : locationCodeRepository.findByIdsForUpdateOrderByIdAsc(codeIds).stream()
                        .collect(Collectors.toMap(LocationCode::getId, c -> c));

        // 3) 持锁后按最新行复核。另一货架的并发重分配可能已动过这两个编码行
        if (oldCode != null) {
            LocationCode oldLocked = lockedMap.get(oldCode.getId());
            if (oldLocked == null || !shelf.getId().equals(oldLocked.getShelfId())) {
                throw new BusinessException(409, "并发操作冲突：原编码的绑定状态已被另一笔操作变更，请刷新后重试");
            }
            oldCode = oldLocked;
        }

        LocationCode newCode;
        if (newCodeId != null) {
            newCode = lockedMap.get(newCodeId);
            if (newCode.getShelfId() != null && !newCode.getShelfId().equals(shelf.getId())) {
                // 后到的一笔：目标编码已被先提交的并发事务挂到别架，本笔失败，旧绑定与流水不变
                throw new BusinessException("新货位编码已被其他货架绑定");
            }
        } else {
            // 新码不存在：直接插入并立即刷库，由 code 唯一约束裁决并发新建
            // （不预加 gap lock——两边各持一把兼容 gap lock 再插入反而会触发 1213 死锁）。
            // 冲突后事务已被标记 rollback-only，翻译成业务提示并整笔回滚，旧绑定与流水不变。
            try {
                newCode = locationCodeRepository.saveAndFlush(LocationCode.builder()
                        .code(request.getNewCode())
                        .status(1)
                        .build());
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(409, "并发操作冲突：货位编码 " + request.getNewCode()
                        + " 刚被另一笔操作绑定，本笔重分配失败，原绑定保持不变");
            }
        }

        if (oldCode != null) {
            oldCode.setShelfId(null);
            oldCode.setStatus(1);
            locationCodeRepository.save(oldCode);
        }

        newCode.setShelfId(shelf.getId());
        newCode.setStatus(2);
        locationCodeRepository.save(newCode);

        // 旧码释放、新码绑定与流水同一事务原子提交，
        // 提交后导出对照表/按编码反查/变更流水看到的必然是同一套现状
        saveChangeLog(shelf.getId(), shelf.getShelfNo(),
                oldCode != null ? oldCode.getCode() : null,
                request.getNewCode(), OPERATION_REASSIGN,
                request.getOperator(), request.getRemark());

        return buildShelfResponse(shelf, newCode, noOccupancy());
    }

    @Override
    public ShelfResponse searchByCode(String code) {
        LocationCode locationCode = locationCodeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("货位编码不存在"));

        if (locationCode.getShelfId() == null) {
            throw new BusinessException("货位编码未绑定货架");
        }

        Shelf shelf = shelfRepository.findById(locationCode.getShelfId())
                .orElseThrow(() -> new BusinessException("关联货架不存在"));

        return buildShelfResponse(shelf, locationCode, loadOccupancy(shelf.getId()));
    }

    @Override
    public List<ZoneTreeResponse> getZoneTree() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByZoneAscShelfNoAsc();
        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, c -> c));

        Map<String, List<ZoneTreeResponse.ShelfItem>> zoneMap = new LinkedHashMap<>();

        for (Shelf shelf : shelves) {
            LocationCode code = codeMap.get(shelf.getId());
            String codeStr = code != null ? code.getCode() : "未绑定";

            ZoneTreeResponse.ShelfItem item = new ZoneTreeResponse.ShelfItem();
            item.setId(shelf.getId());
            item.setShelfNo(shelf.getShelfNo());
            item.setLocationCode(codeStr);
            item.setLabel(shelf.getShelfNo() + " (" + codeStr + ")");

            zoneMap.computeIfAbsent(shelf.getZone(), k -> new ArrayList<>()).add(item);
        }

        return zoneMap.entrySet().stream()
                .map(entry -> {
                    ZoneTreeResponse zone = new ZoneTreeResponse();
                    zone.setLabel(entry.getKey());
                    zone.setValue(entry.getKey());
                    zone.setChildren(entry.getValue());
                    return zone;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeMappingResponse> getAllCodeMappings() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByZoneAscShelfNoAsc();
        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, c -> c));

        return shelves.stream()
                .map(shelf -> {
                    LocationCode code = codeMap.get(shelf.getId());
                    CodeMappingResponse response = new CodeMappingResponse();
                    response.setShelfId(shelf.getId());
                    response.setShelfNo(shelf.getShelfNo());
                    response.setCapacity(shelf.getCapacity());
                    response.setZone(shelf.getZone());
                    response.setLocationCode(code != null ? code.getCode() : "");
                    response.setBindTime(code != null ? code.getUpdatedAt() : null);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeChangeLog> getChangeLogsByShelfId(Long shelfId) {
        return codeChangeLogRepository.findByShelfIdOrderByCreatedAtDesc(shelfId);
    }

    @Override
    public List<CodeChangeLog> getAllChangeLogs() {
        return codeChangeLogRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public String exportCodeMapping() {
        List<CodeMappingResponse> mappings = getAllCodeMappings();

        StringWriter writer = new StringWriter();
        writer.write("货架ID,货架编号,承重(kg),所属库区,货位编码,绑定时间\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (CodeMappingResponse mapping : mappings) {
            writer.write(mapping.getShelfId() + ",");
            writer.write(mapping.getShelfNo() + ",");
            writer.write(mapping.getCapacity() + ",");
            writer.write(mapping.getZone() + ",");
            writer.write(mapping.getLocationCode() != null ? mapping.getLocationCode() : "");
            writer.write(",");
            writer.write(mapping.getBindTime() != null ? mapping.getBindTime().format(formatter) : "");
            writer.write("\n");
        }

        return writer.toString();
    }

    // ===================== 托盘落架占用 =====================

    @Override
    @Transactional
    public ShelfOccupancyResponse landPallet(PalletLandRequest request) {
        String palletNo = request.getPalletNo().trim();
        BigDecimal grossWeight = normalizeWeight(request.getGrossWeight());

        // 1) 锁目标货架行：同一货架的落架/下架/解绑全部串行，
        //    两笔并发落架不可能同时读到旧的在架合计而双双通过
        Shelf shelf = shelfRepository.findByIdForUpdate(request.getShelfId())
                .orElseThrow(() -> new BusinessException("货架不存在"));

        // 2) 没绑货位编码的货架不能落架（落架不是第二条编码绑定）
        LocationCode locationCode = locationCodeRepository.findByShelfId(shelf.getId()).orElse(null);
        if (locationCode == null) {
            throw new BusinessException("货架尚未绑定货位编码，不能落架；请先绑定一对一货位编码");
        }

        // 3) 同一托盘号全局只能在架一次（哪怕落在不同架）
        PalletActive elsewhere = palletActiveRepository.findById(palletNo).orElse(null);
        if (elsewhere != null) {
            throw new BusinessException("托盘 " + palletNo + " 已在架，当前在架于 " + elsewhere.getShelfNo() + "，不能重复落架");
        }

        // 4) 承重校验：单托不得超额定，在架合计+本托不得超额定
        BigDecimal used = palletOccupancyRepository.sumActiveWeightByShelfId(shelf.getId());
        BigDecimal capacity = shelf.getCapacity();
        if (grossWeight.compareTo(capacity) > 0) {
            throw new BusinessException(String.format(
                    "托盘毛重 %skg 已超过货架额定承重 %skg，不能落架",
                    grossWeight.toPlainString(), capacity.toPlainString()));
        }
        BigDecimal afterLand = used.add(grossWeight);
        if (afterLand.compareTo(capacity) > 0) {
            BigDecimal remaining = capacity.subtract(used);
            throw new BusinessException(String.format(
                    "落架后在架合计 %skg 将超过额定承重 %skg（当前在架 %skg，剩余 %skg），不能落架",
                    afterLand.toPlainString(), capacity.toPlainString(),
                    used.toPlainString(), remaining.toPlainString()));
        }

        // 5) 写流水（在架）+ 写在架登记（主键=托盘号），同事务
        PalletOccupancy occupancy = PalletOccupancy.builder()
                .palletNo(palletNo)
                .shelfId(shelf.getId())
                .shelfNo(shelf.getShelfNo())
                .grossWeight(grossWeight)
                .status(PALLET_ON_RACK)
                .landedAt(LocalDateTime.now())
                .landedBy(Optional.ofNullable(request.getOperator()).map(String::trim).orElse(null))
                .landRemark(request.getRemark())
                .build();
        occupancy = palletOccupancyRepository.save(occupancy);

        PalletActive active = PalletActive.builder()
                .palletNo(palletNo)
                .occupancyId(occupancy.getId())
                .shelfId(shelf.getId())
                .shelfNo(shelf.getShelfNo())
                .landedAt(occupancy.getLandedAt())
                .build();
        // 立即刷库，触发 pallet_active 主键唯一约束：
        // 并发下同一托盘号（无论落同一架还是不同架）必然一笔在主键冲突时插入失败。
        // 这里不能在本事务内 try/catch——仓储代理已把事务标记为 rollback-only，吞掉异常会在提交时
        // 抛 UnexpectedRollbackException。正确做法是让异常自然传出、整笔回滚，
        // 由 GlobalExceptionHandler 翻译成友好提示，先前那一托与剩余承重保持不变。
        palletActiveRepository.saveAndFlush(active);

        // 6) 插入后再复核一次在架合计，双保险：任何情况下都不允许把架子压超
        palletOccupancyRepository.flush();
        BigDecimal confirmedUsed = palletOccupancyRepository.sumActiveWeightByShelfId(shelf.getId());
        if (confirmedUsed.compareTo(capacity) > 0) {
            throw new BusinessException(String.format(
                    "落架后在架合计 %skg 超过额定承重 %skg，本部落架失败",
                    confirmedUsed.toPlainString(), capacity.toPlainString()));
        }

        log.info("托盘 {} 落架 {}，毛重 {}kg，落架后在架合计 {}kg/额定 {}kg",
                palletNo, shelf.getShelfNo(), grossWeight, confirmedUsed, capacity);

        return buildOccupancyResponse(shelf, locationCode,
                palletOccupancyRepository.findByShelfIdAndStatusOrderByLandedAtAsc(shelf.getId(), PALLET_ON_RACK),
                confirmedUsed);
    }

    @Override
    @Transactional
    public ShelfOccupancyResponse removePallet(PalletRemoveRequest request) {
        String palletNo = request.getPalletNo().trim();

        PalletActive active = palletActiveRepository.findById(palletNo)
                .orElseThrow(() -> new BusinessException("托盘 " + palletNo + " 当前不在架，无需下架"));

        // 锁它所在的货架行，与该架的落架互斥，退回承重与新落架不会互相覆盖
        Shelf shelf = shelfRepository.findByIdForUpdate(active.getShelfId())
                .orElseThrow(() -> new BusinessException("托盘所在货架不存在"));

        PalletOccupancy occupancy = palletOccupancyRepository
                .findById(active.getOccupancyId())
                .or(() -> palletOccupancyRepository.findByPalletNoAndStatus(palletNo, PALLET_ON_RACK))
                .orElseThrow(() -> new BusinessException("托盘落架流水不存在，数据不一致"));

        if (!Integer.valueOf(PALLET_ON_RACK).equals(occupancy.getStatus())) {
            // 登记与流水不一致时以流水为准并清理，避免卡死
            palletActiveRepository.deleteById(palletNo);
            throw new BusinessException("托盘 " + palletNo + " 已处于下架状态");
        }

        occupancy.setStatus(PALLET_REMOVED);
        occupancy.setRemovedAt(LocalDateTime.now());
        occupancy.setRemovedBy(Optional.ofNullable(request.getOperator()).map(String::trim).orElse(null));
        occupancy.setRemoveRemark(request.getRemark());
        palletOccupancyRepository.save(occupancy);

        // 从在架登记删除，承重即通过“在架流水合计”自动退回
        palletActiveRepository.deleteById(palletNo);
        palletActiveRepository.flush();

        BigDecimal usedAfterRemove = palletOccupancyRepository.sumActiveWeightByShelfId(shelf.getId());
        log.info("托盘 {} 从 {} 下架，退回毛重 {}kg，下架后在架合计 {}kg",
                palletNo, shelf.getShelfNo(), occupancy.getGrossWeight(), usedAfterRemove);

        LocationCode locationCode = locationCodeRepository.findByShelfId(shelf.getId()).orElse(null);
        return buildOccupancyResponse(shelf, locationCode,
                palletOccupancyRepository.findByShelfIdAndStatusOrderByLandedAtAsc(shelf.getId(), PALLET_ON_RACK),
                usedAfterRemove);
    }

    @Override
    public ShelfOccupancyResponse getShelfOccupancy(Long shelfId) {
        Shelf shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new BusinessException("货架不存在"));
        LocationCode locationCode = locationCodeRepository.findByShelfId(shelfId).orElse(null);
        List<PalletOccupancy> pallets =
                palletOccupancyRepository.findByShelfIdAndStatusOrderByLandedAtAsc(shelfId, PALLET_ON_RACK);
        BigDecimal used = sumWeight(pallets);
        return buildOccupancyResponse(shelf, locationCode, pallets, used);
    }

    @Override
    public List<PalletOccupancyResponse> getActivePallets(String shelfNo) {
        return palletOccupancyRepository.findByStatusOrderByLandedAtDesc(PALLET_ON_RACK).stream()
                .filter(o -> shelfNo == null || shelfNo.isBlank() || shelfNo.trim().equals(o.getShelfNo()))
                .map(this::toPalletResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PalletOccupancy> getPalletHistory(String palletNo) {
        if (palletNo == null || palletNo.isBlank()) {
            throw new BusinessException("托盘号不能为空");
        }
        return palletOccupancyRepository.findByPalletNoOrderByLandedAtDesc(palletNo.trim());
    }

    // ===================== 私有组装方法 =====================

    private List<ShelfResponse> enrichShelves(List<Shelf> shelves) {
        if (shelves.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> shelfIds = shelves.stream().map(Shelf::getId).collect(Collectors.toList());

        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, Function.identity()));

        // 在架毛重合计以流水表为准（数据库权威值，关页重开仍一致）
        Map<Long, BigDecimal> weightMap = new LinkedHashMap<>();
        for (ShelfWeightSum row : palletOccupancyRepository.sumActiveWeightByShelfIds(shelfIds)) {
            weightMap.put(row.getShelfId(), normalizeWeight(row.getTotalWeight()));
        }

        // 在架托数以 pallet_active 为准
        Map<Long, Long> countMap = palletActiveRepository.findAll().stream()
                .collect(Collectors.groupingBy(PalletActive::getShelfId, Collectors.counting()));

        return shelves.stream()
                .map(s -> {
                    Occupancy occ = new Occupancy(
                            countMap.getOrDefault(s.getId(), 0L).intValue(),
                            weightMap.getOrDefault(s.getId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    );
                    return buildShelfResponse(s, codeMap.get(s.getId()), occ);
                })
                .collect(Collectors.toList());
    }

    private Occupancy loadOccupancy(Long shelfId) {
        BigDecimal used = normalizeWeight(palletOccupancyRepository.sumActiveWeightByShelfId(shelfId));
        int count = (int) palletActiveRepository.countByShelfId(shelfId);
        return new Occupancy(count, used);
    }

    private Occupancy noOccupancy() {
        return new Occupancy(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private void assertNoActivePallet(Long shelfId, String action) {
        long count = palletActiveRepository.countByShelfId(shelfId);
        if (count > 0) {
            throw new BusinessException("货架上还有 " + count + " 托在架托盘，必须先将托盘全部下架、退回承重后才能" + action);
        }
    }

    private ShelfResponse buildShelfResponse(Shelf shelf, LocationCode locationCode, Occupancy occupancy) {
        ShelfResponse response = new ShelfResponse();
        response.setId(shelf.getId());
        response.setShelfNo(shelf.getShelfNo());
        response.setCapacity(shelf.getCapacity());
        response.setZone(shelf.getZone());
        response.setStatus(shelf.getStatus());
        response.setLocationCode(locationCode != null ? locationCode.getCode() : null);

        BigDecimal used = occupancy != null && occupancy.used != null
                ? normalizeWeight(occupancy.used)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int count = occupancy != null ? occupancy.count : 0;
        response.setPalletCount(count);
        response.setUsedCapacity(used);
        response.setRemainingCapacity(shelf.getCapacity().subtract(used));

        response.setCreatedAt(shelf.getCreatedAt());
        response.setUpdatedAt(shelf.getUpdatedAt());
        return response;
    }

    private ShelfOccupancyResponse buildOccupancyResponse(Shelf shelf, LocationCode locationCode,
                                                          List<PalletOccupancy> pallets, BigDecimal usedRaw) {
        BigDecimal used = normalizeWeight(usedRaw != null ? usedRaw : sumWeight(pallets));
        ShelfOccupancyResponse resp = new ShelfOccupancyResponse();
        resp.setShelfId(shelf.getId());
        resp.setShelfNo(shelf.getShelfNo());
        resp.setZone(shelf.getZone());
        resp.setCapacity(shelf.getCapacity());
        resp.setLocationCode(locationCode != null ? locationCode.getCode() : null);
        resp.setPalletCount(pallets.size());
        resp.setUsedCapacity(used);
        resp.setRemainingCapacity(shelf.getCapacity().subtract(used));
        resp.setPallets(pallets.stream().map(this::toPalletResponse).collect(Collectors.toList()));
        return resp;
    }

    private PalletOccupancyResponse toPalletResponse(PalletOccupancy o) {
        PalletOccupancyResponse r = new PalletOccupancyResponse();
        r.setId(o.getId());
        r.setPalletNo(o.getPalletNo());
        r.setShelfId(o.getShelfId());
        r.setShelfNo(o.getShelfNo());
        r.setGrossWeight(o.getGrossWeight());
        r.setStatus(o.getStatus());
        r.setLandedAt(o.getLandedAt());
        r.setLandedBy(o.getLandedBy());
        r.setLandRemark(o.getLandRemark());
        r.setRemovedAt(o.getRemovedAt());
        r.setRemovedBy(o.getRemovedBy());
        r.setRemoveRemark(o.getRemoveRemark());
        return r;
    }

    private BigDecimal sumWeight(List<PalletOccupancy> pallets) {
        return normalizeWeight(pallets.stream()
                .map(PalletOccupancy::getGrossWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal normalizeWeight(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private void saveChangeLog(Long shelfId, String shelfNo, String oldCode, String newCode,
                               Integer operationType, String operator, String remark) {
        CodeChangeLog logEntry = CodeChangeLog.builder()
                .shelfId(shelfId)
                .shelfNo(shelfNo)
                .oldCode(oldCode)
                .newCode(newCode)
                .operationType(operationType)
                .operator(operator != null ? operator : "system")
                .remark(remark)
                .build();
        codeChangeLogRepository.save(logEntry);
    }

    /** 内部占用快照 */
    private static class Occupancy {
        private final int count;
        private final BigDecimal used;

        private Occupancy(int count, BigDecimal used) {
            this.count = count;
            this.used = used;
        }
    }
}
