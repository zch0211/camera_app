package com.camera.app.asset.controller;

import com.camera.app.asset.dto.AssetCreateRequest;
import com.camera.app.asset.dto.AssetResponse;
import com.camera.app.asset.dto.AssetUpdateRequest;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.service.AssetService;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.security.JwtTokenProvider;
import com.camera.app.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for AssetController.
 *
 * 使用 @Import(SecurityConfig.class) 确保真实的 SecurityFilterChain（含 @EnableMethodSecurity）生效；
 * JwtTokenProvider / UserDetailsService mock 掉，使 JWT 过滤器直接放行；
 * @WithMockUser 注入 SecurityContext，无需真实数据库。
 */
@WebMvcTest(AssetController.class)
@Import(SecurityConfig.class)
class AssetControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AssetService assetService;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    private AssetResponse sampleAsset;

    @BeforeEach
    void setUp() {
        Asset asset = new Asset();
        asset.setIp("192.168.1.100");
        asset.setName("摄像头-A栋1楼");
        asset.setBrand("海康威视");
        asset.setModel("DS-2CD2T85");
        asset.setLocation("A栋1楼大厅");
        asset.setOnline(true);
        asset.setRiskScore(0);
        sampleAsset = new AssetResponse(asset);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. admin 可创建资产
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateAsset() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest();
        req.setIp("192.168.1.100");
        req.setName("摄像头-A栋1楼");
        req.setBrand("海康威视");

        when(assetService.createAsset(any())).thenReturn(sampleAsset);

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ip").value("192.168.1.100"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. admin 可修改资产
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateAsset() throws Exception {
        AssetUpdateRequest req = new AssetUpdateRequest();
        req.setName("摄像头-A栋2楼");

        when(assetService.updateAsset(eq(1L), any())).thenReturn(sampleAsset);

        mockMvc.perform(put("/api/v1/assets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. admin 可删除资产
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteAsset() throws Exception {
        doNothing().when(assetService).deleteAsset(1L);

        mockMvc.perform(delete("/api/v1/assets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4a. operator 可查看资产列表
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanListAssets() throws Exception {
        PageResult<AssetResponse> pageResult =
                new PageResult<>(new PageImpl<>(List.of(sampleAsset), PageRequest.of(0, 20), 1));
        when(assetService.listAssets(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].ip").value("192.168.1.100"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4b. operator 不能创建资产
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotCreateAsset() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest();
        req.setIp("192.168.1.200");
        req.setName("摄像头-测试2");

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4c. operator 不能修改资产
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotUpdateAsset() throws Exception {
        AssetUpdateRequest req = new AssetUpdateRequest();
        req.setName("新名称");

        mockMvc.perform(put("/api/v1/assets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4d. operator 不能删除资产
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotDeleteAsset() throws Exception {
        mockMvc.perform(delete("/api/v1/assets/1"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5a. viewer 不能访问资产列表
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotListAssets() throws Exception {
        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5b. viewer 不能查看资产详情
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotGetAssetDetail() throws Exception {
        mockMvc.perform(get("/api/v1/assets/1"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. 重复 IP 创建失败 → 409
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateIpShouldReturn409() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest();
        req.setIp("192.168.1.100");
        req.setName("重复摄像头");

        when(assetService.createAsset(any()))
                .thenThrow(new BusinessException(409, "IP 已存在: 192.168.1.100"));

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. 查询不存在资产返回 404
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void getAssetNotFoundShouldReturn404() throws Exception {
        when(assetService.getAsset(999L))
                .thenThrow(new BusinessException(404, "资产不存在，id=999"));

        mockMvc.perform(get("/api/v1/assets/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. 校验失败（IP 为空）→ 400
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void createAssetWithBlankIpShouldReturn400() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest();
        req.setIp(""); // 空字符串触发 @NotBlank
        req.setName("摄像头-测试");

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. 校验失败（name 为空）→ 400
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void createAssetWithBlankNameShouldReturn400() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest();
        req.setIp("192.168.1.100");
        req.setName(""); // 空字符串触发 @NotBlank

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
