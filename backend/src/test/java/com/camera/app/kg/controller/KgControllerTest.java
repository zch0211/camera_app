package com.camera.app.kg.controller;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.kg.dto.*;
import com.camera.app.kg.service.CameraKgService;
import com.camera.app.security.JwtTokenProvider;
import com.camera.app.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KgController 切片测试。
 *
 * 使用 @WebMvcTest + @Import(SecurityConfig) 验证安全规则：
 * - ADMIN / OPERATOR 可访问所有 kg 接口
 * - VIEWER 被拒绝（403）
 * - 资产不存在返回 404
 * - 图谱命中 / 未命中时接口均正常返回（200），不报错
 *
 * CameraKgService 被 mock，不连接真实 Neo4j。
 */
@WebMvcTest(KgController.class)
@Import(SecurityConfig.class)
class KgControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CameraKgService cameraKgService;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    private EnrichResponse matchedEnrich;
    private EnrichResponse unmatchedEnrich;
    private VulnHintsResponse matchedVulnHints;
    private VulnHintsResponse unmatchedVulnHints;
    private GraphResponse graphWithData;
    private GraphResponse emptyGraph;

    @BeforeEach
    void setUp() {
        matchedEnrich = EnrichResponse.builder()
                .assetId(1L)
                .matched(true)
                .inferredBrand("海康威视")
                .inferredProduct("DS-2CD2T85")
                .inferredFirmware("V5.7.0")
                .inferredPorts(List.of("80", "554", "8000"))
                .relatedNodesSummary(List.of("产品: DS-2CD2T85", "厂商: 海康威视"))
                .evidencePaths(List.of("产品节点命中: DS-2CD2T85", "关联厂商: 海康威视"))
                .confidence("HIGH")
                .build();

        unmatchedEnrich = EnrichResponse.builder()
                .assetId(2L)
                .matched(false)
                .confidence("LOW")
                .relatedNodesSummary(List.of("图谱中未找到型号 [UnknownModel] 对应的产品节点"))
                .evidencePaths(List.of())
                .build();

        matchedVulnHints = VulnHintsResponse.builder()
                .assetId(1L)
                .matched(true)
                .vulnerabilityHints(List.of(
                        VulnHintItem.builder()
                                .vulnName("CVE-2021-36260")
                                .severity("CRITICAL")
                                .reason("图谱中产品 [DS-2CD2T85] 通过 1 跳关系与漏洞 [CVE-2021-36260] 可能关联")
                                .evidencePath("产品:DS-2CD2T85 → 漏洞:CVE-2021-36260")
                                .confidence("HIGH")
                                .build()
                ))
                .summary("发现 1 条潜在漏洞关联提示（1 条高置信度）")
                .build();

        unmatchedVulnHints = VulnHintsResponse.builder()
                .assetId(2L)
                .matched(false)
                .vulnerabilityHints(List.of())
                .summary("图谱中未找到型号 [UnknownModel] 对应的产品节点")
                .build();

        graphWithData = GraphResponse.builder()
                .nodes(List.of(
                        GraphNode.builder()
                                .id("4:abc:1")
                                .label("DS-2CD2T85")
                                .type("产品")
                                .properties(Map.of("name", "DS-2CD2T85"))
                                .build(),
                        GraphNode.builder()
                                .id("4:abc:2")
                                .label("海康威视")
                                .type("厂商")
                                .properties(Map.of("name", "海康威视"))
                                .build()
                ))
                .edges(List.of(
                        GraphEdge.builder()
                                .source("4:abc:2")
                                .target("4:abc:1")
                                .type("具备")
                                .build()
                ))
                .build();

        emptyGraph = GraphResponse.builder()
                .nodes(List.of())
                .edges(List.of())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. ADMIN 可访问 enrich 接口
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessEnrich() throws Exception {
        when(cameraKgService.enrich(1L)).thenReturn(matchedEnrich);

        mockMvc.perform(get("/api/v1/kg/assets/1/enrich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.confidence").value("HIGH"))
                .andExpect(jsonPath("$.data.inferredBrand").value("海康威视"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. OPERATOR 可访问 vuln-hints 接口
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanAccessVulnHints() throws Exception {
        when(cameraKgService.vulnHints(1L)).thenReturn(matchedVulnHints);

        mockMvc.perform(get("/api/v1/kg/assets/1/vuln-hints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.vulnerabilityHints[0].vulnName").value("CVE-2021-36260"))
                .andExpect(jsonPath("$.data.vulnerabilityHints[0].confidence").value("HIGH"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. OPERATOR 可访问 graph 接口
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanAccessGraph() throws Exception {
        when(cameraKgService.graph(1L)).thenReturn(graphWithData);

        mockMvc.perform(get("/api/v1/kg/assets/1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.edges").isArray())
                .andExpect(jsonPath("$.data.nodes[0].type").value("产品"))
                .andExpect(jsonPath("$.data.edges[0].type").value("具备"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. VIEWER 不能访问 enrich 接口 → 403
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotAccessEnrich() throws Exception {
        mockMvc.perform(get("/api/v1/kg/assets/1/enrich"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. VIEWER 不能访问 vuln-hints 接口 → 403
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotAccessVulnHints() throws Exception {
        mockMvc.perform(get("/api/v1/kg/assets/1/vuln-hints"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. VIEWER 不能访问 graph 接口 → 403
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotAccessGraph() throws Exception {
        mockMvc.perform(get("/api/v1/kg/assets/1/graph"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. 资产不存在 → 404（enrich）
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void enrichAssetNotFoundReturns404() throws Exception {
        when(cameraKgService.enrich(999L))
                .thenThrow(new BusinessException(404, "资产不存在，id=999"));

        mockMvc.perform(get("/api/v1/kg/assets/999/enrich"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. 资产不存在 → 404（vuln-hints）
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void vulnHintsAssetNotFoundReturns404() throws Exception {
        when(cameraKgService.vulnHints(999L))
                .thenThrow(new BusinessException(404, "资产不存在，id=999"));

        mockMvc.perform(get("/api/v1/kg/assets/999/vuln-hints"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. 图谱命中时 enrich 返回完整补全结果
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void enrichWhenGraphHitReturnsFullResult() throws Exception {
        when(cameraKgService.enrich(1L)).thenReturn(matchedEnrich);

        mockMvc.perform(get("/api/v1/kg/assets/1/enrich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.inferredProduct").value("DS-2CD2T85"))
                .andExpect(jsonPath("$.data.inferredFirmware").value("V5.7.0"))
                .andExpect(jsonPath("$.data.inferredPorts[0]").value("80"))
                .andExpect(jsonPath("$.data.evidencePaths").isArray());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 10. 图谱命中时 vuln-hints 返回漏洞提示
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void vulnHintsWhenGraphHitReturnsHints() throws Exception {
        when(cameraKgService.vulnHints(1L)).thenReturn(matchedVulnHints);

        mockMvc.perform(get("/api/v1/kg/assets/1/vuln-hints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.vulnerabilityHints").isArray())
                .andExpect(jsonPath("$.data.vulnerabilityHints[0].evidencePath").isString())
                .andExpect(jsonPath("$.data.summary").isString());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 11. graph 接口返回 nodes + edges
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void graphReturnsNodesAndEdges() throws Exception {
        when(cameraKgService.graph(1L)).thenReturn(graphWithData);

        mockMvc.perform(get("/api/v1/kg/assets/1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.edges").isArray())
                .andExpect(jsonPath("$.data.nodes[0].id").isString())
                .andExpect(jsonPath("$.data.nodes[0].label").isString())
                .andExpect(jsonPath("$.data.edges[0].source").isString())
                .andExpect(jsonPath("$.data.edges[0].target").isString());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 12. 图谱未命中时 enrich 返回合理空结果（200，不报错）
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void enrichWhenGraphMissReturnsUnmatchedResult() throws Exception {
        when(cameraKgService.enrich(2L)).thenReturn(unmatchedEnrich);

        mockMvc.perform(get("/api/v1/kg/assets/2/enrich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.confidence").value("LOW"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 13. 图谱未命中时 vuln-hints 返回空列表（200，不报错）
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void vulnHintsWhenGraphMissReturnsEmptyList() throws Exception {
        when(cameraKgService.vulnHints(2L)).thenReturn(unmatchedVulnHints);

        mockMvc.perform(get("/api/v1/kg/assets/2/vuln-hints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.vulnerabilityHints").isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 14. 图谱未命中时 graph 返回空节点和边（200，不报错）
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void graphWhenGraphMissReturnsEmpty() throws Exception {
        when(cameraKgService.graph(anyLong())).thenReturn(emptyGraph);

        mockMvc.perform(get("/api/v1/kg/assets/2/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodes").isEmpty())
                .andExpect(jsonPath("$.data.edges").isEmpty());
    }
}
