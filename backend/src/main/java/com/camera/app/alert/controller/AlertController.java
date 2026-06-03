package com.camera.app.alert.controller;

import com.camera.app.alert.dto.*;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertSourceType;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.alert.service.AlertService;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "告警管理", description = "告警列表、详情、状态流转。ROOT / ADMIN / OPERATOR 可访问；VIEWER 只读")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // ─── 查询 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "分页查询告警列表",
            description = "权限: ROOT / ADMIN / OPERATOR。支持 keyword / severity / status / sourceType / assetId 过滤，按 createdAt 降序"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<PageResult<AlertListItemResponse>> listAlerts(
            @Parameter(description = "关键词，模糊匹配 title / summary")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "严重等级: CRITICAL / HIGH / MEDIUM / LOW")
            @RequestParam(required = false) AlertSeverity severity,
            @Parameter(description = "状态: NEW / CONFIRMED / FALSE_POSITIVE / RESOLVED / IGNORED")
            @RequestParam(required = false) AlertStatus status,
            @Parameter(description = "来源类型: POC / DISCOVERY / COLLECTION / INFERENCE / RULE")
            @RequestParam(required = false) AlertSourceType sourceType,
            @Parameter(description = "关联资产 ID")
            @RequestParam(required = false) Long assetId,
            @Parameter(description = "页码，从 0 开始，默认 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(alertService.listAlerts(keyword, severity, status, sourceType, assetId, page, size));
    }

    @Operation(
            summary = "查询告警详情",
            description = "权限: ROOT / ADMIN / OPERATOR。返回告警主信息 + 关联资产摘要 + 证据列表 + 操作时间线"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/{id}")
    public ApiResponse<AlertDetailResponse> getAlert(
            @Parameter(description = "告警 ID") @PathVariable Long id) {
        return ApiResponse.ok(alertService.getAlert(id));
    }

    // ─── 创建 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "手工创建告警",
            description = """
                    权限: ROOT / ADMIN / OPERATOR。

                    **必填字段**
                    - title：告警标题
                    - sourceType：来源类型，可选值 POC / DISCOVERY / COLLECTION / INFERENCE / RULE
                      ⚠️ 手工创建请使用 RULE；MANUAL 不是合法值，会返回 400
                    - severity：严重等级，可选值 CRITICAL / HIGH / MEDIUM / LOW

                    **可选字段**
                    - assetId：关联资产 ID；若传入必须是已存在的资产，否则返回 404 "关联资产不存在"
                    - summary / ruleCode / ruleName：补充说明

                    **自动行为**
                    - status 初始为 NEW，triggerCount = 1
                    - 自动写入一条 alert_operations(CREATE)
                    - 若关联了资产，同步写入资产快照并触发该资产 riskScore 重算
                    """
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AlertDetailResponse> createAlert(
            @Valid @RequestBody AlertCreateRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ApiResponse.ok(alertService.createAlert(request, username));
    }

    // ─── 状态流转 ──────────────────────────────────────────────────────────────

    @Operation(
            summary = "确认告警",
            description = "权限: ROOT / ADMIN / OPERATOR。NEW / CONFIRMED → CONFIRMED，写入操作记录并重算风险分"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/confirm")
    public ApiResponse<AlertDetailResponse> confirm(
            @Parameter(description = "告警 ID") @PathVariable Long id,
            @RequestBody(required = false) AlertActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(alertService.confirm(id, comment(request), username(authentication)));
    }

    @Operation(
            summary = "标记误报",
            description = "权限: ROOT / ADMIN / OPERATOR。NEW / CONFIRMED → FALSE_POSITIVE，写入操作记录并重算风险分（误报不计入风险分）"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/false-positive")
    public ApiResponse<AlertDetailResponse> markFalsePositive(
            @Parameter(description = "告警 ID") @PathVariable Long id,
            @RequestBody(required = false) AlertActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(alertService.markFalsePositive(id, comment(request), username(authentication)));
    }

    @Operation(
            summary = "标记已处置",
            description = "权限: ROOT / ADMIN / OPERATOR。NEW / CONFIRMED → RESOLVED，记录处置人/时间并重算风险分"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/resolve")
    public ApiResponse<AlertDetailResponse> resolve(
            @Parameter(description = "告警 ID") @PathVariable Long id,
            @RequestBody(required = false) AlertActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(alertService.resolve(id, comment(request), username(authentication)));
    }

    @Operation(
            summary = "忽略告警",
            description = "权限: ROOT / ADMIN / OPERATOR。NEW / CONFIRMED → IGNORED，写入操作记录并重算风险分"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @PostMapping("/{id}/ignore")
    public ApiResponse<AlertDetailResponse> ignore(
            @Parameter(description = "告警 ID") @PathVariable Long id,
            @RequestBody(required = false) AlertActionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(alertService.ignore(id, comment(request), username(authentication)));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static String username(Authentication auth) {
        return auth != null ? auth.getName() : "unknown";
    }

    private static String comment(AlertActionRequest req) {
        return req != null ? req.getComment() : null;
    }
}
