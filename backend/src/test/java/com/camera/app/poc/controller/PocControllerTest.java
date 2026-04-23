package com.camera.app.poc.controller;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocContentResponse;
import com.camera.app.poc.dto.PocListItemResponse;
import com.camera.app.poc.dto.PocResponse;
import com.camera.app.poc.dto.PocUpdateRequest;
import com.camera.app.poc.entity.*;
import com.camera.app.poc.service.PocDownloadResult;
import com.camera.app.poc.service.PocService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for PocController.
 *
 * 使用 @Import(SecurityConfig.class) 确保真实 SecurityFilterChain（含 @EnableMethodSecurity）生效；
 * JwtTokenProvider / UserDetailsService / PocService mock 掉，无需真实 DB 或 MinIO。
 */
@WebMvcTest(PocController.class)
@Import(SecurityConfig.class)
class PocControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PocService pocService;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    private PocResponse samplePocResponse;
    private PocListItemResponse sampleListItem;

    @BeforeEach
    void setUp() {
        Poc poc = new Poc();
        poc.setName("海康威视摄像头 RCE");
        poc.setCveId("CVE-2024-12345");
        poc.setSeverity(Severity.HIGH);
        poc.setLanguage(Language.PYTHON);
        poc.setTargetType(TargetType.CAMERA);
        poc.setVendor("海康威视");
        poc.setProtocol(Protocol.HTTP);
        poc.setObjectKey("pocs/uuid-test/exploit.py");
        poc.setOriginalFilename("exploit.py");
        poc.setContentType("text/plain");
        poc.setFileSize(1024L);
        poc.setFileSha256("abc123");
        poc.setEnabled(true);
        poc.setStatus(PocStatus.ACTIVE);
        poc.setCreatedBy("admin");
        poc.setCreatedAt(LocalDateTime.now());
        poc.setUpdatedAt(LocalDateTime.now());

        samplePocResponse = new PocResponse(poc);
        sampleListItem = new PocListItemResponse(poc);
    }

    // ─── 1. admin 可上传 POC ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUploadPoc() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exploit.py", "text/plain", "print('poc')".getBytes());

        when(pocService.uploadPoc(any(), anyString(), any(), any(), anyString(), any(),
                anyString(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(samplePocResponse);

        mockMvc.perform(multipart("/api/v1/pocs")
                        .file(file)
                        .param("name", "海康威视摄像头 RCE")
                        .param("severity", "HIGH")
                        .param("targetType", "CAMERA")
                        .param("enabled", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("海康威视摄像头 RCE"))
                .andExpect(jsonPath("$.data.severity").value("HIGH"));
    }

    // ─── 2. admin 可查看 POC 列表 ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListPocs() throws Exception {
        PageResult<PocListItemResponse> page =
                new PageResult<>(new PageImpl<>(List.of(sampleListItem), PageRequest.of(0, 20), 1));
        when(pocService.listPocs(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/pocs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].name").value("海康威视摄像头 RCE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ─── 3. admin 可查看 POC 详情 ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGetPocDetail() throws Exception {
        when(pocService.getPoc(1L)).thenReturn(samplePocResponse);

        mockMvc.perform(get("/api/v1/pocs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cveId").value("CVE-2024-12345"))
                .andExpect(jsonPath("$.data.fileSha256").value("abc123"));
    }

    // ─── 4. admin 可修改 POC 元数据 ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdatePocMetadata() throws Exception {
        PocUpdateRequest req = new PocUpdateRequest();
        req.setName("修改后名称");
        req.setSeverity("CRITICAL");

        when(pocService.updatePoc(eq(1L), any())).thenReturn(samplePocResponse);

        mockMvc.perform(put("/api/v1/pocs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ─── 5. admin 可删除 POC ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeletePoc() throws Exception {
        doNothing().when(pocService).deletePoc(1L);

        mockMvc.perform(delete("/api/v1/pocs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ─── 6a. operator 可查看列表 ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanListPocs() throws Exception {
        PageResult<PocListItemResponse> page =
                new PageResult<>(new PageImpl<>(List.of(sampleListItem), PageRequest.of(0, 20), 1));
        when(pocService.listPocs(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/pocs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ─── 6b. operator 可下载 POC ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanDownloadPoc() throws Exception {
        PocDownloadResult result = new PocDownloadResult(
                "exploit.py", "text/plain", 12L,
                new ByteArrayInputStream("print('poc')".getBytes()));
        when(pocService.downloadPoc(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/pocs/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("exploit.py")));
    }

    // ─── 6c. operator 不能上传 POC ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotUploadPoc() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exploit.py", "text/plain", "poc".getBytes());

        mockMvc.perform(multipart("/api/v1/pocs")
                        .file(file)
                        .param("name", "test")
                        .param("severity", "LOW")
                        .param("targetType", "CAMERA"))
                .andExpect(status().isForbidden());
    }

    // ─── 6d. operator 不能修改 POC ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotUpdatePoc() throws Exception {
        PocUpdateRequest req = new PocUpdateRequest();
        req.setName("新名称");

        mockMvc.perform(put("/api/v1/pocs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ─── 6e. operator 不能删除 POC ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotDeletePoc() throws Exception {
        mockMvc.perform(delete("/api/v1/pocs/1"))
                .andExpect(status().isForbidden());
    }

    // ─── 7. viewer 不能访问 POC 接口 ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotListPocs() throws Exception {
        mockMvc.perform(get("/api/v1/pocs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotGetPocDetail() throws Exception {
        mockMvc.perform(get("/api/v1/pocs/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotDownloadPoc() throws Exception {
        mockMvc.perform(get("/api/v1/pocs/1/download"))
                .andExpect(status().isForbidden());
    }

    // ─── 8. 空文件上传失败 → 400 ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadEmptyFileShouldReturn400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.py", "text/plain", new byte[0]);

        when(pocService.uploadPoc(any(), anyString(), any(), any(), anyString(), any(),
                anyString(), any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new BusinessException(400, "上传文件不能为空"));

        mockMvc.perform(multipart("/api/v1/pocs")
                        .file(emptyFile)
                        .param("name", "empty test")
                        .param("severity", "LOW")
                        .param("targetType", "CAMERA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ─── 9. 查询不存在的 POC → 404 ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPocNotFoundShouldReturn404() throws Exception {
        when(pocService.getPoc(999L))
                .thenThrow(new BusinessException(404, "POC 不存在，id=999"));

        mockMvc.perform(get("/api/v1/pocs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ─── 10. 内容预览：admin 可查看 .py 文件 ─────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanPreviewPyFile() throws Exception {
        Poc poc = buildPoc("exploit.py");
        PocContentResponse resp = PocContentResponse.previewable(poc, "import requests\nprint('poc')", false);
        when(pocService.getPocContent(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/pocs/1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.previewable").value(true))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.contentPreview").value("import requests\nprint('poc')"));
    }

    // ─── 11. 内容预览：operator 可查看 .py 文件 ──────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanPreviewPyFile() throws Exception {
        Poc poc = buildPoc("exploit.py");
        PocContentResponse resp = PocContentResponse.previewable(poc, "import requests", false);
        when(pocService.getPocContent(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/pocs/1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewable").value(true));
    }

    // ─── 12. 内容预览：viewer 不能访问 ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotPreviewPocContent() throws Exception {
        mockMvc.perform(get("/api/v1/pocs/1/content"))
                .andExpect(status().isForbidden());
    }

    // ─── 13. 内容预览：不存在的 POC → 404 ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void contentPreviewNotFoundShouldReturn404() throws Exception {
        when(pocService.getPocContent(999L))
                .thenThrow(new BusinessException(404, "POC 不存在，id=999"));

        mockMvc.perform(get("/api/v1/pocs/999/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ─── 14. 内容预览：二进制文件 → previewable=false ─────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void binaryFileReturnsNotPreviewable() throws Exception {
        Poc poc = buildPoc("exploit.exe");
        PocContentResponse resp = PocContentResponse.notPreviewable(poc, "file type is not previewable");
        when(pocService.getPocContent(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/pocs/1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewable").value(false))
                .andExpect(jsonPath("$.data.message").value("file type is not previewable"));
    }

    // ─── 15. 内容预览：超大文件 → truncated=true ─────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void largeFileReturnsTruncated() throws Exception {
        Poc poc = buildPoc("big.py");
        PocContentResponse resp = PocContentResponse.previewable(poc, "# truncated content...", true);
        when(pocService.getPocContent(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/pocs/1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewable").value(true))
                .andExpect(jsonPath("$.data.truncated").value(true));
    }

    // ─── 辅助方法 ─────────────────────────────────────────────────────────────

    private Poc buildPoc(String filename) {
        Poc poc = new Poc();
        poc.setName("test poc");
        poc.setLanguage(Language.PYTHON);
        poc.setTargetType(TargetType.CAMERA);
        poc.setSeverity(Severity.HIGH);
        poc.setObjectKey("pocs/uuid/" + filename);
        poc.setOriginalFilename(filename);
        poc.setContentType("text/plain");
        poc.setFileSize(1024L);
        poc.setEnabled(true);
        poc.setStatus(PocStatus.ACTIVE);
        poc.setCreatedAt(LocalDateTime.now());
        poc.setUpdatedAt(LocalDateTime.now());
        return poc;
    }
}
