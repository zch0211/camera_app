package com.camera.app.poc.controller;

import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.poc.dto.PocExecutionLogResponse;
import com.camera.app.poc.dto.PocExecutionLogSummary;
import com.camera.app.poc.service.PocExecutionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "POC 执行历史", description = "查询 POC 执行记录。ADMIN/OPERATOR 可查看，VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/poc-executions")
@RequiredArgsConstructor
public class PocExecutionController {

    private final PocExecutionLogService pocExecutionLogService;

    @Operation(
            summary = "分页查询执行记录列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。支持 pocId / assetId / success 过滤，按 createdAt 降序排列。"
                    + "列表不含 stdout/stderr，详情接口才包含完整输出"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。包含 stdout / stderr 完整内容"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}")
    public ApiResponse<PocExecutionLogResponse> getById(
            @Parameter(description = "执行记录 ID") @PathVariable Long id) {
        return ApiResponse.ok(pocExecutionLogService.getById(id));
    }
}
