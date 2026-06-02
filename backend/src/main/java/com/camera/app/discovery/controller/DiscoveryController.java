package com.camera.app.discovery.controller;

import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.discovery.dto.*;
import com.camera.app.discovery.entity.DiscoveryLevel;
import com.camera.app.discovery.entity.DiscoverySourceType;
import com.camera.app.discovery.service.DiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "资产发现", description = "主动扫描与被动嗅探，发现结果批量纳管资产")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @Operation(summary = "创建发现任务（ACTIVE_SCAN / PASSIVE_SNIFF），异步执行，立即返回 PENDING")
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DiscoveryTaskResponse> createTask(@Valid @RequestBody DiscoveryTaskCreateRequest req) {
        return ApiResponse.ok(discoveryService.createTask(req));
    }

    @Operation(summary = "查询发现任务列表（分页）")
    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<PageResult<DiscoveryTaskResponse>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(discoveryService.listTasks(page, size));
    }

    @Operation(summary = "查询发现任务详情")
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DiscoveryTaskResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.ok(discoveryService.getTask(taskId));
    }

    @Operation(summary = "停止发现任务（PENDING/RUNNING → CANCELED），已发现结果保留")
    @PostMapping("/tasks/{taskId}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DiscoveryTaskResponse> stopTask(@PathVariable Long taskId) {
        return ApiResponse.ok(discoveryService.stopTask(taskId));
    }

    @Operation(summary = "查询发现结果列表（支持按 managed / sourceType / deviceType / keyword 筛选）")
    @GetMapping("/tasks/{taskId}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<PageResult<DiscoveryResultResponse>> listResults(
            @PathVariable Long taskId,
            @Parameter(description = "是否已纳管") @RequestParam(required = false) Boolean managed,
            @Parameter(description = "来源类型: SCAN / SNIFF")
            @RequestParam(required = false) DiscoverySourceType sourceType,
            @Parameter(description = "设备类型候选: CAMERA / NVR / ROUTER / OTHER")
            @RequestParam(required = false) String deviceType,
            @Parameter(description = "关键词，匹配 IP / hostname / vendorHint")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(discoveryService.listResults(
                taskId, managed, sourceType, deviceType, keyword, page, size));
    }

    @Operation(summary = "跨任务发现结果总览（taskId 可选，支持 managed / sourceType / deviceType / discoveryLevel / keyword 筛选）")
    @GetMapping("/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<PageResult<DiscoveryResultResponse>> listAllResults(
            @Parameter(description = "任务ID，不传则查询所有任务结果") @RequestParam(required = false) Long taskId,
            @Parameter(description = "是否已纳管") @RequestParam(required = false) Boolean managed,
            @Parameter(description = "来源类型: SCAN / SNIFF") @RequestParam(required = false) DiscoverySourceType sourceType,
            @Parameter(description = "设备类型候选: CAMERA / NVR / ROUTER / OTHER") @RequestParam(required = false) String deviceType,
            @Parameter(description = "发现层级: ALIVE / CANDIDATE") @RequestParam(required = false) DiscoveryLevel discoveryLevel,
            @Parameter(description = "关键词，匹配 IP / hostname / vendorHint") @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(discoveryService.listAllResults(
                taskId, managed, sourceType, deviceType, discoveryLevel, keyword, page, size));
    }

    @Operation(summary = "批量将发现结果导入资产库，已存在的跳过不重复")
    @PostMapping("/results/batch-import")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<BatchImportResponse> batchImport(@Valid @RequestBody BatchImportRequest req) {
        return ApiResponse.ok(discoveryService.batchImport(req));
    }

    @Operation(summary = "将单条发现结果导入资产库")
    @PostMapping("/results/{resultId}/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DiscoveryTaskResponse> importSingle(
            @PathVariable Long resultId,
            @RequestParam(required = false) String defaultLocation,
            @RequestParam(required = false) Long defaultOrgId) {
        return ApiResponse.ok(discoveryService.importSingle(resultId, defaultLocation, defaultOrgId));
    }
}
