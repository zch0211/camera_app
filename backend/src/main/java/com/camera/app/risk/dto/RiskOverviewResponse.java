package com.camera.app.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "风险总览统计")
public class RiskOverviewResponse {

    @Schema(description = "告警总数")
    private final long totalAlerts;

    @Schema(description = "有效未关闭告警数（NEW + CONFIRMED）")
    private final long openAlerts;

    @Schema(description = "CRITICAL 级有效告警数")
    private final long criticalAlerts;

    @Schema(description = "HIGH 级有效告警数")
    private final long highAlerts;

    @Schema(description = "MEDIUM 级有效告警数")
    private final long mediumAlerts;

    @Schema(description = "LOW 级有效告警数")
    private final long lowAlerts;

    @Schema(description = "已确认告警数（CONFIRMED）")
    private final long confirmedAlerts;

    @Schema(description = "误报告警数（FALSE_POSITIVE）")
    private final long falsePositiveAlerts;

    @Schema(description = "已处置告警数（RESOLVED）")
    private final long resolvedAlerts;

    @Schema(description = "已忽略告警数（IGNORED）")
    private final long ignoredAlerts;

    @Schema(description = "高风险资产数量（riskScore ≥ 1）")
    private final long highRiskAssets;
}
