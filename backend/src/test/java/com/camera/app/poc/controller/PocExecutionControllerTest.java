package com.camera.app.poc.controller;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.entity.PocExecutionLog;
import com.camera.app.poc.service.PocExecutionLogService;
import com.camera.app.security.JwtTokenProvider;
import com.camera.app.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PocExecutionController.class)
@Import(SecurityConfig.class)
class PocExecutionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PocExecutionLogService pocExecutionLogService;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    private PocExecutionLogSummary sampleSummary;
    private PocExecutionLogResponse sampleDetail;

    @BeforeEach
    void setUp() {
        PocExecutionLog log = new PocExecutionLog();
        log.setId(1L);
        log.setPocId(10L);
        log.setAssetId(null);
        log.setExecutedBy("admin");
        log.setSuccess(true);
        log.setExitCode(0);
        log.setStdout("hello poc");
        log.setStderr("");
        log.setTruncated(false);
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        log.setDurationMs(120L);

        sampleSummary = new PocExecutionLogSummary(log);
        sampleDetail = new PocExecutionLogResponse(log);
    }

    // ─── 1. admin 可查看执行记录列表 ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListExecutions() throws Exception {
        PageResult<PocExecutionLogSummary> page =
                new PageResult<>(new PageImpl<>(List.of(sampleSummary), PageRequest.of(0, 20), 1));
        when(pocExecutionLogService.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/poc-executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].executedBy").value("admin"))
                .andExpect(jsonPath("$.data.content[0].success").value(true));
    }

    // ─── 2. operator 可查看执行记录列表 ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanListExecutions() throws Exception {
        PageResult<PocExecutionLogSummary> page =
                new PageResult<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(pocExecutionLogService.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/poc-executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ─── 3. viewer 不能查看执行记录列表 ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotListExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/poc-executions"))
                .andExpect(status().isForbidden());
    }

    // ─── 4. admin 可查看执行记录详情 ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGetExecutionDetail() throws Exception {
        when(pocExecutionLogService.getById(1L)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/poc-executions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pocId").value(10))
                .andExpect(jsonPath("$.data.stdout").value("hello poc"))
                .andExpect(jsonPath("$.data.exitCode").value(0));
    }

    // ─── 5. viewer 不能查看执行记录详情 ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotGetExecutionDetail() throws Exception {
        mockMvc.perform(get("/api/v1/poc-executions/1"))
                .andExpect(status().isForbidden());
    }

    // ─── 6. 不存在的记录 → 404 ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void notFoundReturns404() throws Exception {
        when(pocExecutionLogService.getById(999L))
                .thenThrow(new BusinessException(404, "执行记录不存在，id=999"));

        mockMvc.perform(get("/api/v1/poc-executions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ─── 7. 支持 pocId 过滤参数 ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void listSupportsPocIdFilter() throws Exception {
        PageResult<PocExecutionLogSummary> page =
                new PageResult<>(new PageImpl<>(List.of(sampleSummary), PageRequest.of(0, 20), 1));
        when(pocExecutionLogService.list(eq(10L), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/poc-executions").param("pocId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].pocId").value(10));
    }
}
