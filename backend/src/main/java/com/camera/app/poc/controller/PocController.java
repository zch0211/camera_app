package com.camera.app.poc.controller;

import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocListItemResponse;
import com.camera.app.poc.dto.PocResponse;
import com.camera.app.poc.dto.PocUpdateRequest;
import com.camera.app.poc.service.PocDownloadResult;
import com.camera.app.poc.service.PocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "POC 仓库", description = "POC 文件上传、下载、元数据管理。ADMIN 全权限；OPERATOR 可查看/下载；VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/pocs")
@RequiredArgsConstructor
public class PocController {

    private final PocService pocService;

    // ─── 列表 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "分页查询 POC 列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。支持 keyword / severity / enabled / language / targetType 过滤"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<PageResult<PocListItemResponse>> listPocs(
            @Parameter(description = "关键词（模糊匹配 name / cveId / description）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "严重等级：LOW / MEDIUM / HIGH / CRITICAL")
            @RequestParam(required = false) String severity,
            @Parameter(description = "是否启用：true / false，不传=全部")
            @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "语言：PYTHON / JAVA / GO / SHELL / OTHER")
            @RequestParam(required = false) String language,
            @Parameter(description = "目标类型：CAMERA / ROUTER / NVR / PLATFORM / OTHER")
            @RequestParam(required = false) String targetType,
            @Parameter(description = "页码（从 0 开始），默认 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(pocService.listPocs(keyword, severity, enabled, language, targetType, page, size));
    }

    // ─── 详情 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "查询 POC 详情",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}")
    public ApiResponse<PocResponse> getPoc(
            @Parameter(description = "POC ID") @PathVariable Long id) {
        return ApiResponse.ok(pocService.getPoc(id));
    }

    // ─── 上传 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "上传 POC 文件及元数据",
            description = "权限: ROLE_ADMIN。multipart/form-data 同时提交文件和元数据。language 可不传，系统自动根据扩展名推断。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PocResponse> uploadPoc(
            @Parameter(description = "POC 文件（≤50 MB，类型不限）", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "POC 名称", required = true)
            @RequestParam String name,
            @Parameter(description = "描述")
            @RequestParam(required = false) String description,
            @Parameter(description = "CVE 编号，如 CVE-2024-12345")
            @RequestParam(required = false) String cveId,
            @Parameter(description = "严重等级：LOW / MEDIUM / HIGH / CRITICAL", required = true)
            @RequestParam String severity,
            @Parameter(description = "文件语言：PYTHON / JAVA / GO / SHELL / OTHER（不传则自动推断）")
            @RequestParam(required = false) String language,
            @Parameter(description = "目标类型：CAMERA / ROUTER / NVR / PLATFORM / OTHER", required = true)
            @RequestParam String targetType,
            @Parameter(description = "适用厂商")
            @RequestParam(required = false) String vendor,
            @Parameter(description = "协议类型：HTTP / HTTPS / RTSP / ONVIF / TCP / UDP / OTHER")
            @RequestParam(required = false) String protocol,
            @Parameter(description = "执行入口点（预留字段，供后续扫描执行器使用）")
            @RequestParam(required = false) String entryPoint,
            @Parameter(description = "是否启用，默认 true")
            @RequestParam(defaultValue = "true") boolean enabled,
            Authentication authentication) {

        String createdBy = authentication != null ? authentication.getName() : "unknown";
        return ApiResponse.ok(pocService.uploadPoc(
                file, name, description, cveId, severity, language,
                targetType, vendor, protocol, entryPoint, enabled, createdBy));
    }

    // ─── 下载 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "下载 POC 文件",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。响应 Content-Disposition: attachment，触发浏览器下载"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadPoc(
            @Parameter(description = "POC ID") @PathVariable Long id) {

        PocDownloadResult result = pocService.downloadPoc(id);
        String encodedFilename = URLEncoder.encode(result.originalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(new InputStreamResource(result.inputStream()));
    }

    // ─── 修改元数据 ────────────────────────────────────────────────────────────

    @Operation(
            summary = "修改 POC 元数据",
            description = "权限: ROLE_ADMIN。只修改元数据，不替换文件本体。所有字段均为可选，仅传入需修改的字段"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<PocResponse> updatePoc(
            @Parameter(description = "POC ID") @PathVariable Long id,
            @Valid @RequestBody PocUpdateRequest request) {
        return ApiResponse.ok(pocService.updatePoc(id, request));
    }

    // ─── 删除（逻辑删除）──────────────────────────────────────────────────────

    @Operation(
            summary = "删除/下架 POC",
            description = "权限: ROLE_ADMIN。逻辑删除：status → DELETED，enabled → false，同步删除 MinIO 文件"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePoc(
            @Parameter(description = "POC ID") @PathVariable Long id) {
        pocService.deletePoc(id);
        return ApiResponse.ok(null);
    }
}
