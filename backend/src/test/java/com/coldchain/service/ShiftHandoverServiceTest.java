package com.coldchain.service;

import com.coldchain.dto.request.ShiftCheckRequest;
import com.coldchain.dto.request.ShiftHandoverSubmitRequest;
import com.coldchain.dto.request.ShiftOpenRequest;
import com.coldchain.dto.request.ShiftSealRequest;
import com.coldchain.dto.response.ShiftHandoverResponse;
import com.coldchain.dto.response.ShiftZoneSummaryResponse;
import com.coldchain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 库区交接班业务规则集成测试（H2，真实 JPA + 悲观锁 + 唯一约束）。
 *
 * 不加类级 @Transactional：开班串行锁内部使用 REQUIRES_NEW 独立事务，且并发用例跨线程执行，
 * 外层测试事务既无法传播到新线程，也会干扰独立小事务。每个用例使用独立库区名天然隔离。
 */
@SpringBootTest
class ShiftHandoverServiceTest {

    @Autowired
    private ShiftHandoverService service;

    /** 每个用例独立库区，互不污染（无需回滚） */
    private final String zone = "Z" + UUID.randomUUID().toString().substring(0, 8);

    private ShiftOpenRequest openReq(String zone, String type, String person) {
        ShiftOpenRequest req = new ShiftOpenRequest();
        req.setZone(zone);
        req.setShiftType(type);
        req.setOutgoingName(person);
        return req;
    }

    private ShiftHandoverResponse openShift(String zone, String person) {
        return service.openShift(openReq(zone, "白班", person));
    }

    private ShiftHandoverResponse openMine(String person) {
        return openShift(zone, person);
    }

    private void check(Long shiftId, String code, boolean checked) {
        ShiftCheckRequest req = new ShiftCheckRequest();
        req.setShiftId(shiftId);
        req.setItemCode(code);
        req.setChecked(checked);
        service.updateCheck(req);
    }

    private void checkAll(Long shiftId) {
        check(shiftId, "APPEARANCE", true);
        check(shiftId, "DOOR_CURTAIN", true);
        check(shiftId, "SEAL", true);
    }

    private void seal(Long shiftId, String sealNo) {
        ShiftSealRequest req = new ShiftSealRequest();
        req.setShiftId(shiftId);
        req.setSealNo(sealNo);
        service.updateSealNo(req);
    }

    private ShiftHandoverResponse handover(Long shiftId, String incoming, String note) {
        ShiftHandoverSubmitRequest req = new ShiftHandoverSubmitRequest();
        req.setShiftId(shiftId);
        req.setIncomingName(incoming);
        req.setHandoverNote(note);
        return service.submitHandover(req);
    }

    private ShiftZoneSummaryResponse summaryOf(String zoneName) {
        return service.getZoneSummary().stream()
                .filter(z -> zoneName.equals(z.getZone())).findFirst().orElseThrow();
    }

    @Test
    void 开班即生成三项未勾选必检() {
        ShiftHandoverResponse shift = openMine("张三");
        assertEquals(1, shift.getStatus());
        assertEquals("进行中", shift.getStatusText());
        assertEquals(3, shift.getTotalItems());
        assertEquals(0, shift.getCheckedItems());
        assertFalse(shift.getAllChecked());
        assertNull(shift.getIncomingName());
        assertNull(shift.getSealNo());
    }

    @Test
    void 漏勾交不出去() {
        ShiftHandoverResponse shift = openMine("张三");
        // 只勾两项，铅封那项不勾；铅封号先填好，确保失败原因就是漏勾
        check(shift.getId(), "APPEARANCE", true);
        check(shift.getId(), "DOOR_CURTAIN", true);
        seal(shift.getId(), "SEAL-001");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> handover(shift.getId(), "李四", "交接"));
        assertTrue(ex.getMessage().contains("必检项还没勾齐"), ex.getMessage());
        assertEquals(1, service.getShift(shift.getId()).getStatus());
    }

    @Test
    void 铅封号空着交不出去() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> handover(shift.getId(), "李四", "交接"));
        assertTrue(ex.getMessage().contains("铅封号"), ex.getMessage());
        assertEquals(1, service.getShift(shift.getId()).getStatus());
    }

    @Test
    void 接班人与当班人同名交不出去() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        seal(shift.getId(), "SEAL-002");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> handover(shift.getId(), " 张三 ", "自己接自己"));
        assertTrue(ex.getMessage().contains("同一个人"), ex.getMessage());
        assertEquals(1, service.getShift(shift.getId()).getStatus());
    }

    @Test
    void 接班人为空白交不出去() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        seal(shift.getId(), "SEAL-003");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> handover(shift.getId(), "   ", "x"));
        assertTrue(ex.getMessage().contains("接班人"), ex.getMessage());
    }

    @Test
    void 勾齐且铅封号与接班人合法才能交班() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        seal(shift.getId(), "SEAL-100");

        ShiftHandoverResponse handed = handover(shift.getId(), "李四", "门帘完好，铅封已点");
        assertEquals(2, handed.getStatus());
        assertEquals("已交班", handed.getStatusText());
        assertEquals("李四", handed.getIncomingName());
        assertEquals("SEAL-100", handed.getSealNo());
        assertNotNull(handed.getHandedAt());
        assertTrue(handed.getAllChecked());
    }

    @Test
    void 已交班不许再改勾选() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        seal(shift.getId(), "SEAL-101");
        handover(shift.getId(), "李四", "ok");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> check(shift.getId(), "APPEARANCE", false));
        assertTrue(ex.getMessage().contains("已经交班"), ex.getMessage());
        ShiftHandoverResponse reloaded = service.getShift(shift.getId());
        assertEquals(1, reloaded.getCheckItems().get(0).getChecked());
    }

    @Test
    void 已交班不许把铅封号改成空_也不许改号() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        seal(shift.getId(), "SEAL-102");
        handover(shift.getId(), "李四", "ok");

        ShiftSealRequest blank = new ShiftSealRequest();
        blank.setShiftId(shift.getId());
        blank.setSealNo("  ");
        BusinessException ex1 = assertThrows(BusinessException.class, () -> service.updateSealNo(blank));
        assertTrue(ex1.getMessage().contains("已经交班"), ex1.getMessage());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> seal(shift.getId(), "SEAL-999"));
        assertTrue(ex2.getMessage().contains("已经交班"), ex2.getMessage());

        assertEquals("SEAL-102", service.getShift(shift.getId()).getSealNo());
    }

    @Test
    void 进行中的班铅封号不能清空() {
        ShiftHandoverResponse shift = openMine("张三");
        seal(shift.getId(), "SEAL-103");

        ShiftSealRequest blank = new ShiftSealRequest();
        blank.setShiftId(shift.getId());
        blank.setSealNo("");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateSealNo(blank));
        assertTrue(ex.getMessage().contains("铅封号"), ex.getMessage());
        assertEquals("SEAL-103", service.getShift(shift.getId()).getSealNo());
    }

    @Test
    void 同库区上一班没交完_再开新班被拦住_先开的班原样不动() {
        ShiftHandoverResponse first = openMine("张三");
        check(first.getId(), "APPEARANCE", true); // 第一班点检到一半

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.openShift(openReq(zone, "夜班", "王五")));
        assertTrue(ex.getMessage().contains("还没交班"), ex.getMessage());

        // 先开的班保持原样：仍是进行中、仍是张三、勾选没有被冲掉
        ShiftHandoverResponse unchanged = service.getShift(first.getId());
        assertEquals("张三", unchanged.getOutgoingName());
        assertEquals(1, unchanged.getStatus());
        assertEquals(1, unchanged.getCheckedItems());
    }

    @Test
    void 交完上一班后同库区才能开下一班() {
        ShiftHandoverResponse first = openMine("张三");
        checkAll(first.getId());
        seal(first.getId(), "SEAL-200");
        handover(first.getId(), "李四", "白班交夜班");

        ShiftHandoverResponse second = service.openShift(openReq(zone, "夜班", "李四"));
        assertEquals(1, second.getStatus());
        assertEquals("李四", second.getOutgoingName());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void 不同库区互不影响可同时各开一班() {
        String otherZone = "Z" + UUID.randomUUID().toString().substring(0, 8);
        ShiftHandoverResponse a = openMine("张三");
        ShiftHandoverResponse b = openShift(otherZone, "赵六");
        assertNotEquals(a.getId(), b.getId());
        assertTrue(summaryOf(zone).getHasOpenShift());
        assertTrue(summaryOf(otherZone).getHasOpenShift());
    }

    @Test
    void 交班后三处对齐同一班_重新查询也一致() {
        ShiftHandoverResponse shift = openMine("张三");
        checkAll(shift.getId());
        seal(shift.getId(), "SEAL-300");
        handover(shift.getId(), "李四", "台账一致");

        // 1) 点检明细（班次详情）
        ShiftHandoverResponse detail = service.getShift(shift.getId());
        // 2) 交班流水
        List<ShiftHandoverResponse> logs = service.getHandoverLogs();
        ShiftHandoverResponse inLog = logs.stream()
                .filter(h -> h.getId().equals(shift.getId())).findFirst().orElseThrow();
        // 3) 按库区反查当班人：已交班后本库区无进行中班，上一班接班人为李四
        ShiftZoneSummaryResponse summary = summaryOf(zone);

        assertEquals(detail.getId(), inLog.getId());
        assertEquals(2, inLog.getStatus());
        assertEquals("张三", inLog.getOutgoingName());
        assertEquals("李四", inLog.getIncomingName());
        assertEquals("SEAL-300", inLog.getSealNo());
        assertEquals(3, inLog.getCheckedItems());
        assertFalse(summary.getHasOpenShift());
        assertNull(summary.getCurrentOutgoingName());
        assertEquals("李四", summary.getLastIncomingName());
        assertEquals(1L, summary.getHandedCount());

        // “关掉再进来”：全部重新查一遍，是数据库里的同一份数据，不是各写各的内存态
        ShiftHandoverResponse detailAgain = service.getShift(shift.getId());
        assertEquals("SEAL-300", detailAgain.getSealNo());
        assertEquals("李四", detailAgain.getIncomingName());
        assertEquals(3, detailAgain.getCheckedItems());
    }

    @Test
    void 按库区反查进行中当班人() {
        ShiftHandoverResponse shift = service.openShift(openReq(zone, "夜班", "钱七"));
        ShiftZoneSummaryResponse summary = summaryOf(zone);
        assertTrue(summary.getHasOpenShift());
        assertEquals("钱七", summary.getCurrentOutgoingName());
        assertEquals("夜班", summary.getCurrentShiftType());
        assertEquals(shift.getId(), summary.getCurrentShiftId());
    }

    @Test
    void 同库区并发开班只有一个成功_先开的保持原样() throws Exception {
        String cz = "并发区" + UUID.randomUUID().toString().substring(0, 8);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String person = "并发员" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    service.openShift(openReq(cz, "白班", person));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    // 被“上一班未交班”拦下（行锁路径）
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    // 数据库唯一索引兜底等其它冲突也算“被拦住”，不能静默吞掉
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, success.get(), "只允许开出一个班");
        assertEquals(threads - 1, rejected.get(), "其余并发开班必须全部被拦");

        List<ShiftHandoverResponse> zoneShifts = service.getShiftsByZone(cz);
        long openCount = zoneShifts.stream().filter(h -> h.getStatus() == 1).count();
        assertEquals(1, openCount);
        ShiftHandoverResponse theShift = zoneShifts.stream().filter(h -> h.getStatus() == 1).findFirst().get();
        assertEquals(0, theShift.getCheckedItems());
        assertTrue(theShift.getOutgoingName().startsWith("并发员"));
    }
}
