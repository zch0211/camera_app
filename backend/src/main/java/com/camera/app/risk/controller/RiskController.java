package com.camera.app.risk.controller;

import com.camera.app.alert.dto.AlertListItemResponse;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertSourceType;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.alert.service.AlertRiskScoreService;
import com.camera.app.alert.service.AlertService;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.risk.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "风险概览", description = "资产风险排行与告警统计接口。ROOT / ADMIN / OPERATOR 可访问")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final AlertRiskScoreService riskScoreService;
    private final AlertService          alertService;

    // ── 高风险资产 ─────────────────────────────────────────────────────────────

    @Operation(
            summary = "高风险资产列表",
            description = """
                    权限: ROOT / ADMIN / OPERATOR。按 riskScore 降序分页，返回各资产的开放告警数量、最高等级和最近触发时间。

                    **过滤规则**
                    - 默认只返回 riskScore ≥ 1 的资产（真正有风险的资产），零风险资产不出现
                    - 传 minScore=0 返回全部资产

                    **riskScore 计算规则**
                    - 仅统计状态为 NEW / CONFIRMED 的告警
                    - FALSE_POSITIVE / RESOLVED / IGNORED 状态的告警不参与计算
                    - CRITICAL=40 / HIGH=25 / MEDIUM=10 / LOW=5，累加后上限 100
                    - 每次告警状态变化后自动重算并回写 assets.risk_score
                    """
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/high-risk-assets")
    public ApiResponse<PageResult<HighRiskAssetResponse>> highRiskAssets(
            @Parameter(description = "最低风险分过滤（含）。不传默认 1；传 0 返回全部资产")
            @RequestParam(required = false) Integer minScore,
            @Parameter(description = "页码，从 0 开始，默认 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(riskScoreService.highRiskAssets(minScore, page, size));
    }

    // ── 风险总览 ───────────────────────────────────────────────────────────────

    @Operation(
            summary = "风险总览统计",
            description = "权限: ROOT / ADMIN / OPERATOR。返回告警分类计数与高风险资产数，供风险总览页展示"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/overview")
    public ApiResponse<RiskOverviewResponse> overview() {
        return ApiResponse.ok(riskScoreService.getOverview());
    }

    @Operation(
            summary = "告警趋势（按天）",
            description = "权限: ROOT / ADMIN / OPERATOR。返回最近 N 天每天的新增告警总数与有效告警数，默认 7 天"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/alert-trends")
    public ApiResponse<List<AlertTrendEntry>> alertTrends(
            @Parameter(description = "统计天数，默认 7，最大 90")
            @RequestParam(defaultValue = "7") int days) {
        int effectiveDays = Math.min(Math.max(days, 1), 90);
        return ApiResponse.ok(riskScoreService.getAlertTrends(effectiveDays));
    }

    @Operation(
            summary = "告警来源分布",
            description = "权限: ROOT / ADMIN / OPERATOR。按 sourceType 统计告警数量，用于饼图/柱状图展示"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/source-distribution")
    public ApiResponse<List<SourceDistributionEntry>> sourceDistribution() {
        return ApiResponse.ok(riskScoreService.getSourceDistribution());
    }

    @Operation(
            summary = "最近告警列表",
            description = "权限: ROOT / ADMIN / OPERATOR。返回最近若干条告警（默认 10），含资产摘要"
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/latest-alerts")
    public ApiResponse<PageResult<AlertListItemResponse>> latestAlerts(
            @Parameter(description = "返回条数，默认 10，最大 50")
            @RequestParam(defaultValue = "10") int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 50);
        return ApiResponse.ok(alertService.listAlerts(null, null, null, null, null, 0, effectiveLimit));
    }
}
