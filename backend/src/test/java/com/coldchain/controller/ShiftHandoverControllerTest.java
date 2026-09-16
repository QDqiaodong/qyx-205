package com.coldchain.controller;

import com.coldchain.dto.request.ShiftCheckRequest;
import com.coldchain.dto.request.ShiftHandoverSubmitRequest;
import com.coldchain.dto.request.ShiftOpenRequest;
import com.coldchain.dto.request.ShiftSealRequest;
import com.coldchain.dto.response.ShiftHandoverResponse;
import com.coldchain.service.ShiftHandoverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 交接班 HTTP 端到端：走真实 Spring MVC + 全局异常处理，
 * 验证接口路径、参数校验、业务拦截与“三处对齐”的响应结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShiftHandoverControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ShiftHandoverService service;

    private ShiftHandoverResponse openShift() {
        ShiftOpenRequest req = new ShiftOpenRequest();
        req.setZone("接口区");
        req.setShiftType("夜班");
        req.setOutgoingName("周甲");
        return service.openShift(req);
    }

    private void checkAll(Long shiftId) {
        for (String code : new String[]{"APPEARANCE", "DOOR_CURTAIN", "SEAL"}) {
            ShiftCheckRequest c = new ShiftCheckRequest();
            c.setShiftId(shiftId);
            c.setItemCode(code);
            c.setChecked(true);
            service.updateCheck(c);
        }
    }

    @Test
    void 开班参数缺失返回业务错误码而非500() throws Exception {
        ShiftOpenRequest req = new ShiftOpenRequest();
        req.setZone("接口区");
        // 缺 shiftType、outgoingName
        mockMvc.perform(post("/api/shift/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("班次")));
    }

    @Test
    void 完整交班流程_接口层三处对齐() throws Exception {
        ShiftHandoverResponse shift = openShift();
        checkAll(shift.getId());
        ShiftSealRequest seal = new ShiftSealRequest();
        seal.setShiftId(shift.getId());
        seal.setSealNo("API-888");
        service.updateSealNo(seal);

        // 漏接班人 → 交不出去
        ShiftHandoverSubmitRequest bad = new ShiftHandoverSubmitRequest();
        bad.setShiftId(shift.getId());
        mockMvc.perform(post("/api/shift/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 同名接班人 → 交不出去
        ShiftHandoverSubmitRequest same = new ShiftHandoverSubmitRequest();
        same.setShiftId(shift.getId());
        same.setIncomingName("周甲");
        same.setHandoverNote("自己接自己");
        mockMvc.perform(post("/api/shift/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(same)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("同一个人")));

        // 合法交班
        ShiftHandoverSubmitRequest ok = new ShiftHandoverSubmitRequest();
        ok.setShiftId(shift.getId());
        ok.setIncomingName("吴乙");
        ok.setHandoverNote("门帘完好，铅封号 API-888");
        mockMvc.perform(post("/api/shift/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.incomingName").value("吴乙"))
                .andExpect(jsonPath("$.data.sealNo").value("API-888"))
                .andExpect(jsonPath("$.data.checkedItems").value(3));

        // 交班流水里同一 shiftId
        mockMvc.perform(get("/api/shift/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(shift.getId()))
                .andExpect(jsonPath("$.data[0].incomingName").value("吴乙"));

        // 按库区反查：接口区已无进行中班，上一班接班人吴乙
        mockMvc.perform(get("/api/shift/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.zone=='接口区')].hasOpenShift").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.data[?(@.zone=='接口区')].lastIncomingName").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("吴乙"))));
    }

    @Test
    void 已交班后接口再打空铅封号被冻结拦截() throws Exception {
        ShiftHandoverResponse shift = openShift();
        checkAll(shift.getId());
        ShiftSealRequest seal = new ShiftSealRequest();
        seal.setShiftId(shift.getId());
        seal.setSealNo("API-999");
        service.updateSealNo(seal);

        ShiftHandoverSubmitRequest ok = new ShiftHandoverSubmitRequest();
        ok.setShiftId(shift.getId());
        ok.setIncomingName("吴乙");
        service.submitHandover(ok);

        // 已交班：尝试把铅封号清空 —— 冻结，返回 400
        ShiftSealRequest blank = new ShiftSealRequest();
        blank.setShiftId(shift.getId());
        blank.setSealNo("");
        mockMvc.perform(post("/api/shift/seal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blank)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已经交班")));

        // 已交班：尝试取消勾选 —— 同样冻结
        ShiftCheckRequest uncheck = new ShiftCheckRequest();
        uncheck.setShiftId(shift.getId());
        uncheck.setItemCode("APPEARANCE");
        uncheck.setChecked(false);
        mockMvc.perform(post("/api/shift/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uncheck)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已经交班")));

        // 铅封号原样仍是 API-999
        mockMvc.perform(get("/api/shift/" + shift.getId()))
                .andExpect(jsonPath("$.data.sealNo").value("API-999"));
    }

    @Test
    void 同库区重复开班接口返回409_先开的不动() throws Exception {
        openShift();

        ShiftOpenRequest second = new ShiftOpenRequest();
        second.setZone("接口区");
        second.setShiftType("白班");
        second.setOutgoingName("后来的人");
        mockMvc.perform(post("/api/shift/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("还没交班")));
    }
}
