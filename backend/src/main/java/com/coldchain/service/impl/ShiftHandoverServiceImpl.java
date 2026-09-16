package com.coldchain.service.impl;

import com.coldchain.dto.request.ShiftCheckRequest;
import com.coldchain.dto.request.ShiftHandoverSubmitRequest;
import com.coldchain.dto.request.ShiftOpenRequest;
import com.coldchain.dto.request.ShiftSealRequest;
import com.coldchain.dto.response.ShiftCheckItemResponse;
import com.coldchain.dto.response.ShiftHandoverResponse;
import com.coldchain.dto.response.ShiftZoneSummaryResponse;
import com.coldchain.entity.ShiftCheckItem;
import com.coldchain.entity.ShiftHandover;
import com.coldchain.entity.ShiftZoneLock;
import com.coldchain.exception.BusinessException;
import com.coldchain.repository.ShiftCheckItemRepository;
import com.coldchain.repository.ShiftHandoverRepository;
import com.coldchain.repository.ShiftZoneLockRepository;
import com.coldchain.repository.ShelfRepository;
import com.coldchain.service.ShiftHandoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftHandoverServiceImpl implements ShiftHandoverService {

    private final ShiftHandoverRepository shiftRepository;
    private final ShiftCheckItemRepository checkItemRepository;
    private final ShiftZoneLockRepository zoneLockRepository;
    private final ShelfRepository shelfRepository;
    private final ShiftZoneLockEnsurer zoneLockEnsurer;

    /** 班次状态：1进行中 2已交班 */
    private static final int STATUS_OPEN = 1;
    private static final int STATUS_HANDED = 2;

    /** 必检项编码 */
    public static final String ITEM_APPEARANCE = "APPEARANCE";
    public static final String ITEM_DOOR_CURTAIN = "DOOR_CURTAIN";
    public static final String ITEM_SEAL = "SEAL";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 必检项固定三项，顺序即点检顺序（外观 → 门帘 → 铅封号） */
    private static final String[][] REQUIRED_ITEMS = {
            {ITEM_APPEARANCE, "库区外观完好（货架、地面、货物外观无异常）"},
            {ITEM_DOOR_CURTAIN, "保温门帘完好（无破损、闭合正常）"},
            {ITEM_SEAL, "铅封已点验并登记铅封号"}
    };

    @Override
    @Transactional
    public ShiftHandoverResponse openShift(ShiftOpenRequest request) {
        String zone = requireText(request.getZone(), "库区");
        String shiftType = normalizeShiftType(request.getShiftType());
        String outgoing = requireText(request.getOutgoingName(), "当班人姓名");

        // 1) 先锁本库区的开班串行锁行：同一库区的并发开班在此完全串行。
        //    不锁 shelf 行——交接班不碰货位编码配对，也不碰托盘承重，只动交接班自己的表。
        lockZone(zone);

        // 2) 持锁后检查上一班是否已交完。后到者必然能看到先到者已提交的进行中班 → 被拦住，
        //    本笔不插入任何数据，先开的那一班原样不动。
        ShiftHandover existing = shiftRepository.findByZoneAndStatus(zone, STATUS_OPEN).orElse(null);
        if (existing != null) {
            throw new BusinessException(409, "库区【" + zone + "】上一班（" + existing.getShiftType()
                    + "，当班人 " + existing.getOutgoingName()
                    + "）还没交班，不能开新班；请先由当班人完成点检并交班");
        }

        // 3) 插入进行中的班，并同事务插入三项未勾选的必检项：班与明细原子可见，
        //    不会出现“有班无明细”的半成品。
        //    saveAndFlush 立即刷库：极端竞争下行锁漏判时，由 shift_handover 的
        //    (status=1 时 zone 唯一) 部分唯一索引裁决——后来这班插入失败、整笔回滚，先开的班原样不动。
        ShiftHandover shift;
        try {
            shift = shiftRepository.saveAndFlush(ShiftHandover.builder()
                    .zone(zone)
                    .shiftType(shiftType)
                    .status(STATUS_OPEN)
                    .outgoingName(outgoing)
                    .openedAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(409, "库区【" + zone
                    + "】上一班还没交班，新班被拦下，先开的那一班保持原样");
        }

        List<ShiftCheckItem> items = new ArrayList<>(REQUIRED_ITEMS.length);
        for (String[] item : REQUIRED_ITEMS) {
            items.add(ShiftCheckItem.builder()
                    .shiftId(shift.getId())
                    .itemCode(item[0])
                    .itemName(item[1])
                    .checked(0)
                    .build());
        }
        checkItemRepository.saveAll(items);

        log.info("库区 {} 开出 {}，班次ID={}，当班人={}", zone, shiftType, shift.getId(), outgoing);
        return toResponse(shift, items);
    }

    @Override
    public ShiftHandoverResponse getShift(Long id) {
        ShiftHandover shift = getExistingShift(id);
        return buildResponse(shift);
    }

    @Override
    public List<ShiftHandoverResponse> getShiftsByZone(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new BusinessException("库区不能为空");
        }
        return shiftRepository.findByZoneOrderByOpenedAtDescIdDesc(zone.trim()).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Override
    public List<ShiftHandoverResponse> getHandoverLogs() {
        // 交班流水只取已交班的班，按交班时间倒序
        return shiftRepository.findByStatusOrderByHandedAtDescIdDesc(STATUS_HANDED).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Override
    public List<ShiftZoneSummaryResponse> getZoneSummary() {
        List<ShiftHandover> all = shiftRepository.findAllByOrderByOpenedAtDescIdDesc();

        // 库区集合：已建档货架的库区 + 已有交接班记录的库区，保证没有货架的库区也能反查当班人
        Set<String> zones = new LinkedHashSet<>(shelfRepository.findDistinctZone());
        for (ShiftHandover h : all) {
            zones.add(h.getZone());
        }

        Map<String, List<ShiftHandover>> byZone = new LinkedHashMap<>();
        for (String z : zones) {
            byZone.put(z, new ArrayList<>());
        }
        for (ShiftHandover h : all) {
            byZone.get(h.getZone()).add(h);
        }

        List<ShiftZoneSummaryResponse> result = new ArrayList<>(zones.size());
        for (Map.Entry<String, List<ShiftHandover>> entry : byZone.entrySet()) {
            List<ShiftHandover> shifts = entry.getValue();

            ShiftHandover open = shifts.stream()
                    .filter(h -> Integer.valueOf(STATUS_OPEN).equals(h.getStatus()))
                    .findFirst()
                    .orElse(null);

            ShiftHandover lastHanded = shifts.stream()
                    .filter(h -> Integer.valueOf(STATUS_HANDED).equals(h.getStatus()))
                    .findFirst()
                    .orElse(null);

            long handedCount = shifts.stream()
                    .filter(h -> Integer.valueOf(STATUS_HANDED).equals(h.getStatus()))
                    .count();

            ShiftZoneSummaryResponse row = new ShiftZoneSummaryResponse();
            row.setZone(entry.getKey());
            row.setHasOpenShift(open != null);
            row.setCurrentOutgoingName(open != null ? open.getOutgoingName() : null);
            row.setCurrentShiftType(open != null ? open.getShiftType() : null);
            row.setCurrentShiftId(open != null ? open.getId() : null);
            row.setCurrentOpenedAt(open != null ? formatTime(open.getOpenedAt()) : null);
            row.setLastIncomingName(lastHanded != null ? lastHanded.getIncomingName() : null);
            row.setHandedCount(handedCount);
            result.add(row);
        }

        // 稳定排序：A区、B区…优先按库区名自然顺序
        result.sort(Comparator.comparing(ShiftZoneSummaryResponse::getZone,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    @Transactional
    public ShiftHandoverResponse updateCheck(ShiftCheckRequest request) {
        if (request.getShiftId() == null) {
            throw new BusinessException("班次ID不能为空");
        }
        String itemCode = requireText(request.getItemCode(), "必检项编码");
        boolean checked = Boolean.TRUE.equals(request.getChecked());

        // 1) 先锁班行并复核状态：已交班的班直接拒绝，勾选改动进不来
        ShiftHandover shift = lockOpenShift(request.getShiftId());

        // 2) 再锁本班明细行，同一班的两个并发勾选在此串行
        List<ShiftCheckItem> items = checkItemRepository.findByShiftIdForUpdate(shift.getId());
        ShiftCheckItem target = items.stream()
                .filter(i -> itemCode.equals(i.getItemCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("必检项不存在: " + itemCode));

        target.setChecked(checked ? 1 : 0);
        target.setCheckedAt(checked ? LocalDateTime.now() : null);
        if (request.getRemark() != null) {
            target.setRemark(normalizeNullable(request.getRemark()));
        }
        checkItemRepository.save(target);

        log.info("班次 {}（库区 {}）必检项 {} {}", shift.getId(), shift.getZone(), itemCode,
                checked ? "已勾选" : "已取消勾选");
        return toResponse(shift, items);
    }

    @Override
    @Transactional
    public ShiftHandoverResponse updateSealNo(ShiftSealRequest request) {
        if (request.getShiftId() == null) {
            throw new BusinessException("班次ID不能为空");
        }
        // 先锁班行：已交班的班一律冻结（改号/改空都在这挡住）；进行中的班再校验铅封号不许清空
        ShiftHandover shift = lockOpenShift(request.getShiftId());
        String sealNo = requireText(request.getSealNo(), "铅封号");
        shift.setSealNo(sealNo);
        shiftRepository.save(shift);

        log.info("班次 {}（库区 {}）登记铅封号 {}", shift.getId(), shift.getZone(), sealNo);
        return buildResponse(shift);
    }

    @Override
    @Transactional
    public ShiftHandoverResponse submitHandover(ShiftHandoverSubmitRequest request) {
        if (request.getShiftId() == null) {
            throw new BusinessException("班次ID不能为空");
        }
        String incoming = requireText(request.getIncomingName(), "接班人姓名");
        String note = normalizeNullable(request.getHandoverNote());

        // 1) 锁班行：同一班的交班/勾选/改铅封号在此串行，锁内状态是权威的
        ShiftHandover shift = lockOpenShift(request.getShiftId());

        // 2) 锁明细并复核三项全部勾齐，漏勾任何一项都交不出去
        List<ShiftCheckItem> items = checkItemRepository.findByShiftIdForUpdate(shift.getId());
        List<String> uncheckedNames = items.stream()
                .filter(i -> !Integer.valueOf(1).equals(i.getChecked()))
                .map(ShiftCheckItem::getItemName)
                .toList();
        if (!uncheckedNames.isEmpty()) {
            throw new BusinessException("必检项还没勾齐，不能交班，未勾项：" + String.join("、", uncheckedNames));
        }

        // 3) 铅封号必须已登记，空着交不出去
        if (shift.getSealNo() == null || shift.getSealNo().isBlank()) {
            throw new BusinessException("铅封号还没登记，不能交班；请先点验铅封并填写铅封号");
        }

        // 4) 接班人不能跟当班人是同一个人（白夜班换人，自己交给自己不算交接）
        if (incoming.equals(shift.getOutgoingName())) {
            throw new BusinessException("接班人不能与当班人是同一个人（" + incoming + "），本班交不出去");
        }

        // 5) 全部通过：置为已交班并冻结。班、明细同事务提交，
        //    提交后点检勾选/交班流水/库区反查当班人三处读到的就是同一 shiftId 的同一班
        shift.setStatus(STATUS_HANDED);
        shift.setIncomingName(incoming);
        shift.setHandoverNote(note);
        shift.setHandedAt(LocalDateTime.now());
        shiftRepository.save(shift);

        log.info("库区 {} 的 {}（班次ID={}）已交班：{} → {}，铅封号 {}",
                shift.getZone(), shift.getShiftType(), shift.getId(),
                shift.getOutgoingName(), incoming, shift.getSealNo());
        return toResponse(shift, items);
    }

    // ===================== 私有方法 =====================

    /**
     * 取本库区开班串行锁。
     * 先在独立事务里确保“每库区一行”的锁行已提交（并发首开的主键冲突只回滚那个独立事务），
     * 再在当前开班事务里对该行 FOR UPDATE 加锁，直到本事务提交才释放。
     */
    private void lockZone(String zone) {
        ShiftZoneLock lockRow = zoneLockRepository.findByZoneForUpdate(zone);
        if (lockRow == null) {
            zoneLockEnsurer.ensureLockRowExists(zone);
        }
        zoneLockEnsurer.lockExistingRow(zone);
    }

    /** 锁班行并强制要求“进行中”：已交班的班任何改动都在这里被挡住 */
    private ShiftHandover lockOpenShift(Long shiftId) {
        ShiftHandover shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new BusinessException("班次不存在"));
        if (Integer.valueOf(STATUS_HANDED).equals(shift.getStatus())) {
            throw new BusinessException("库区【" + shift.getZone() + "】这一班已经交班（"
                    + shift.getOutgoingName() + " → " + shift.getIncomingName()
                    + "），点检勾选与铅封号已冻结，不能再改");
        }
        return shift;
    }

    private ShiftHandover getExistingShift(Long id) {
        if (id == null) {
            throw new BusinessException("班次ID不能为空");
        }
        return shiftRepository.findById(id)
                .orElseThrow(() -> new BusinessException("班次不存在"));
    }

    private ShiftHandoverResponse buildResponse(ShiftHandover shift) {
        List<ShiftCheckItem> items = checkItemRepository.findByShiftIdOrderByIdAsc(shift.getId());
        return toResponse(shift, items);
    }

    private ShiftHandoverResponse toResponse(ShiftHandover shift, List<ShiftCheckItem> items) {
        ShiftHandoverResponse resp = new ShiftHandoverResponse();
        resp.setId(shift.getId());
        resp.setZone(shift.getZone());
        resp.setShiftType(shift.getShiftType());
        resp.setStatus(shift.getStatus());
        resp.setStatusText(Integer.valueOf(STATUS_HANDED).equals(shift.getStatus()) ? "已交班" : "进行中");
        resp.setOutgoingName(shift.getOutgoingName());
        resp.setIncomingName(shift.getIncomingName());
        resp.setSealNo(shift.getSealNo());
        resp.setHandoverNote(shift.getHandoverNote());
        resp.setOpenedAt(formatTime(shift.getOpenedAt()));
        resp.setHandedAt(formatTime(shift.getHandedAt()));

        int total = items.size();
        long checked = items.stream().filter(i -> Integer.valueOf(1).equals(i.getChecked())).count();
        resp.setTotalItems(total);
        resp.setCheckedItems((int) checked);
        resp.setAllChecked(total > 0 && checked == total);

        resp.setCheckItems(items.stream().map(i -> {
            ShiftCheckItemResponse r = new ShiftCheckItemResponse();
            r.setId(i.getId());
            r.setShiftId(i.getShiftId());
            r.setItemCode(i.getItemCode());
            r.setItemName(i.getItemName());
            r.setChecked(i.getChecked());
            r.setCheckedAt(formatTime(i.getCheckedAt()));
            r.setRemark(i.getRemark());
            return r;
        }).toList());
        return resp;
    }

    private String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(field + "不能为空");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeShiftType(String shiftType) {
        String v = requireText(shiftType, "班次").replace(" ", "");
        if ("白班".equals(v) || "夜班".equals(v)) {
            return v;
        }
        // 容错：兼容 day/night 之类的输入，统一落中文
        String lower = v.toLowerCase();
        if ("day".equals(lower)) {
            return "白班";
        }
        if ("night".equals(lower)) {
            return "夜班";
        }
        throw new BusinessException("班次只能是白班或夜班");
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? time.format(FORMATTER) : null;
    }
}
