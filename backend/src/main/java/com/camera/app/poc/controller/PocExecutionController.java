package com.camera.app.poc.controller;

import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.service.PocDownloadResult;
import com.camera.app.poc.service.PocExecutionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Tag(name = "POC 执行历史", description = "查询 POC 执行记录及附件下载。ADMIN/OPERATOR 可查看，VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/poc-executions")
@RequiredArgsConstructor
public class PocExecutionController {

    private final PocExecutionLogService pocExecutionLogService;

    @Operation(
            summary = "分页查询执行记录列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。支持 pocId / assetId / success 过滤，按 createdAt 降序排列。"
                    + "列表不含 stdout/stderr，详情接口才包含完整输出。hasArtifacts=true 表示该次执行产生了文件附件"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<PageResult<PocExecutionLogSummary>> list(
            @Parameter(description = "过滤指定 POC ID") @RequestParam(required = false) Long pocId,
            @Parameter(description = "过滤指定资产 ID") @RequestParam(required = false) Long assetId,
            @Parameter(description = "过滤执行结果：true=成功 / false=失败") @RequestParam(required = false) Boolean success,
            @Parameter(description = "页码（从 0 开始），默认 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(pocExecutionLogService.list(pocId, assetId, success, page, size));
    }

    @Operation(
            summary = "查询执行记录详情",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。包含 stdout / stderr 完整内容及 artifactSummary（JSON 格式的附件元数据）"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/{id}")
    public ApiResponse<PocExecutionLogResponse> getById(
            @Parameter(description = "执行记录 ID") @PathVariable Long id) {
        return ApiResponse.ok(pocExecutionLogService.getById(id));
    }

    @Operation(
            summary = "在线预览执行结果附件",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。"
                    + "以 inline 方式返回附件字节流，供浏览器直接渲染（如 <img src='...?token=xxx'>）。"
                    + "支持 ?token=<jwt> 查询参数，用于浏览器图片标签等无法设置 Authorization 头的场景。"
                    + "IMAGE 类型返回 image/jpeg 等原始 Content-Type；FILE 类型同样 inline 返回。"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/{id}/artifacts/{name:.+}/preview")
    public void previewArtifact(
            @Parameter(description = "执行记录 ID") @PathVariable Long id,
            @Parameter(description = "附件文件名（可含点号）") @PathVariable String name,
            HttpServletResponse response) throws IOException {
        log.debug("[ARTIFACT-PREVIEW] logId={} name={}", id, name);
        // downloadArtifact() opens a MinIO InputStream — wrap everything in try-with-resources
        // so the stream is guaranteed closed even if header-writing or transfer fails
        PocDownloadResult result = pocExecutionLogService.downloadArtifact(id, name);
        try (InputStream is = result.inputStream()) {
            String contentType = result.contentType() != null ? result.contentType() : "application/octet-stream";
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline; filename=\"" + result.originalFilename() + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            if (result.fileSize() != null) {
                response.setContentLengthLong(result.fileSize());
            }
            is.transferTo(response.getOutputStream());
            log.debug("[ARTIFACT-PREVIEW] done logId={} name={} size={}", id, name, result.fileSize());
        } catch (IOException ioe) {
            // Response may already be partially committed at this point — log only
            log.warn("[ARTIFACT-PREVIEW] IO error while streaming logId={} name={}: {}", id, name, ioe.getMessage());
            throw ioe;
        }
    }

    @Operation(
            summary = "下载执行结果附件",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。"
                    + "以 attachment 方式强制下载附件，浏览器将弹出保存对话框。"
                    + "支持 ?token=<jwt> 查询参数，用于直接链接跳转下载等无法设置 Authorization 头的场景。"
                    + "附件名称从执行结果的 artifacts[*].name 获取，或通过执行记录详情的 artifactSummary 解析"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/{id}/artifacts/{name:.+}/download")
    public void downloadArtifact(
            @Parameter(description = "执行记录 ID") @PathVariable Long id,
            @Parameter(description = "附件文件名（可含点号）") @PathVariable String name,
            HttpServletResponse response) throws IOException {
        log.debug("[ARTIFACT-DOWNLOAD] logId={} name={}", id, name);
        PocDownloadResult result = pocExecutionLogService.downloadArtifact(id, name);
        try (InputStream is = result.inputStream()) {
            String contentType = result.contentType() != null ? result.contentType() : "application/octet-stream";
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + result.originalFilename() + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            if (result.fileSize() != null) {
                response.setContentLengthLong(result.fileSize());
            }
            is.transferTo(response.getOutputStream());
            log.debug("[ARTIFACT-DOWNLOAD] done logId={} name={} size={}", id, name, result.fileSize());
        } catch (IOException ioe) {
            log.warn("[ARTIFACT-DOWNLOAD] IO error while streaming logId={} name={}: {}", id, name, ioe.getMessage());
            throw ioe;
        }
    }
}
