package com.camera.app.collection.controller;

import com.camera.app.collection.dto.CollectionResultResponse;
import com.camera.app.collection.dto.CollectionTaskCreateRequest;
import com.camera.app.collection.dto.CollectionTaskResponse;
import com.camera.app.collection.service.AssetCollectionService;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据采集", description = "对单个资产发起轻量主动采集任务，探测开放端口与 HTTP/HTTPS 基础信息，并自动写回设备画像。" +
        "ADMIN / OPERATOR 可读写")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets/{id}/collection-tasks")
@RequiredArgsConstructor
public class AssetCollectionController {

    private final AssetCollectionService collectionService;

    @Operation(
            summary = "发起采集任务（异步执行）",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。\n" +
                    "立即返回任务信息（status=PENDING），后台异步执行探测流程：\n" +
                    "1. 端口可达性探测（TCP connect）\n" +
                    "2. HTTP/HTTPS 页面标题与 Server 响应头抓取\n" +
                    "3. RTSP / ONVIF / SNMP / SSH / Telnet / UPnP 协议探测\n\n" +
                    "任务状态流转：PENDING → RUNNING → SUCCESS / FAILED。\n" +
                    "请通过 GET /collection-tasks/{taskId} 轮询任务状态，或通过 GET /collection-tasks/{taskId}/results 查看原始结果。\n" +
                    "采集完成后自动写回 AssetTechnicalProfile 并生成 AssetEvidence 记录。"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CollectionTaskResponse> createTask(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @RequestBody(required = false) CollectionTaskCreateRequest request) {
        if (request == null) request = new CollectionTaskCreateRequest();
        return ApiResponse.ok(collectionService.createAndExecuteTask(id, request));
    }

    @Operation(
            summary = "查询资产的采集任务列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。按创建时间降序分页返回"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<PageResult<CollectionTaskResponse>> listTasks(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "页码，从 0 开始，默认 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(collectionService.listTasks(id, page, size));
    }

    @Operation(
            summary = "查询采集任务详情",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。返回任务状态、摘要、结果数量等信息"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{taskId}")
    public ApiResponse<CollectionTaskResponse> getTask(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return ApiResponse.ok(collectionService.getTask(id, taskId));
    }

    @Operation(
            summary = "查询任务下的原始采集结果列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。" +
                    "返回该任务所有探测记录，包含每个端口的 PORT_SCAN 结果与 HTTP_TITLE 结果。" +
                    "parsedData 字段为 JSON 字符串，包含端口/协议/标题/Server 等结构化信息"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{taskId}/results")
    public ApiResponse<List<CollectionResultResponse>> getTaskResults(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return ApiResponse.ok(collectionService.getTaskResults(id, taskId));
    }
}
