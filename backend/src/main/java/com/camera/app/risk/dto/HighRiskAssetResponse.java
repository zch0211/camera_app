package com.camera.app.risk.dto;

import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.entity.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "高风险资产摘要")
public class HighRiskAssetResponse {

    private final Long assetId;
    private final String name;
    private final String ip;
    private final AssetType type;
    private final int riskScore;
    private final long openAlertCount;
    private final AlertSeverity highestSeverity;
    private final LocalDateTime latestAlertAt;

    public HighRiskAssetResponse(Asset asset, long openAlertCount,
                                 AlertSeverity highestSeverity,
                                 LocalDateTime latestAlertAt) {
        this.assetId        = asset.getId();
        this.name           = asset.getName();
        this.ip             = asset.getIp();
        this.type           = asset.getType();
        this.riskScore      = asset.getRiskScore() != null ? asset.getRiskScore() : 0;
        this.openAlertCount = openAlertCount;
        this.highestSeverity = highestSeverity;
        this.latestAlertAt  = latestAlertAt;
    }
}
