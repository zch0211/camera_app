package com.camera.app.alert.dto;

import com.camera.app.alert.entity.Alert;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertSourceType;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.asset.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "告警列表项")
public class AlertListItemResponse {

    private final Long id;
    private final String title;
    private final AlertSourceType sourceType;
    private final AlertSeverity severity;
    private final AlertStatus status;

    @Schema(description = "关联资产 ID，若资产已删除则为 null")
    private final Long assetId;

    @Schema(description = "资产名称；资产存在时取当前值，资产已删除时取创建快照")
    private final String assetName;

    @Schema(description = "资产 IP；资产存在时取当前值，资产已删除时取创建快照")
    private final String assetIp;

    @Schema(description = "true 表示该告警曾关联某资产但该资产已被删除（快照数据）")
    private final boolean assetDeleted;

    private final LocalDateTime createdAt;
    private final LocalDateTime lastTriggeredAt;
    private final int triggerCount;

    public AlertListItemResponse(Alert alert, Asset asset) {
        this.id              = alert.getId();
        this.title           = alert.getTitle();
        this.sourceType      = alert.getSourceType();
        this.severity        = alert.getSeverity();
        this.status          = alert.getStatus();
        this.assetId         = alert.getAssetId();

        // Prefer current asset data; fall back to snapshot when asset was deleted
        this.assetName = asset != null ? asset.getName() : alert.getAssetNameSnapshot();
        this.assetIp   = asset != null ? asset.getIp()   : alert.getAssetIpSnapshot();

        // assetId is null + snapshot exists  →  asset was deleted after alert was created
        this.assetDeleted = alert.getAssetId() == null && alert.getAssetNameSnapshot() != null;

        this.createdAt       = alert.getCreatedAt();
        this.lastTriggeredAt = alert.getLastTriggeredAt();
        this.triggerCount    = alert.getTriggerCount();
    }
}
