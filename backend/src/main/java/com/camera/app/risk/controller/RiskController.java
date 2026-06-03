package com.camera.app.risk.controller;

import com.camera.app.alert.service.AlertRiskScoreService;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.risk.dto.HighRiskAssetResponse;
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

@Tag(name = "风险概览", description = "资产风险排行与高风险资产列表。ROOT / ADMIN / OPERATOR 可访问")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final AlertRiskScoreService riskScoreService;

    @Operation(
            summary = "高风险资产列表",
            description = """
                    权限: ROOT / ADMIN / OPERATOR。

                    **过滤规则**
                    - 默认只返回 riskScore ≥ 1 的资产（真正有风险的资产），零风险资产不出现
                    - 可通过 minScore 参数自定义阈值；传 minScore=0 返回全部资产

                    **riskScore 计算规则**
                    - 仅统计状态为 NEW / CONFIRMED 的告警
                    - FALSE_POSITIVE / RESOLVED / IGNORED 状态的告警不参与计算
                    - CRITICAL=40 / HIGH=25 / MEDIUM=10 / LOW=5，累加后上限 100
                    - 每次告警状态变化后自动重算并回写 assets.risk_score

                    **返回字段**
                    - openAlertCount：该资产当前 NEW+CONFIRMED 告警数量
                    - highestSeverity：当前最高告警等级
                    - latestAlertAt：最近一次触发时间
                    """
    )
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'OPERATOR')")
    @GetMapping("/high-risk-assets")
    public ApiResponse<PageResult<HighRiskAssetResponse>> highRiskAssets(
            @Parameter(description = "最低风险分过滤（含）。不传默认 1，即只返回 riskScore≥1 的真正高风险资产；传 0 返回全部资产")
            @RequestParam(required = false) Integer minScore,
            @Parameter(description = "页码，从 0 开始，默认 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(riskScoreService.highRiskAssets(minScore, page, size));
    }
}
