package com.camera.app.alert.dto;

import com.camera.app.alert.entity.*;
import com.camera.app.asset.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Schema(description = "告警详情（含证据与操作时间线）")
public class AlertDetailResponse {

    // ── 告警主信息 ──────────────────────────────────
    private final Long id;
    private final String title;
    private final AlertSourceType sourceType;
    private final AlertSeverity severity;
    private final AlertStatus status;
    private final String summary;
    private final String ruleCode;
    private final String ruleName;
    private final String fingerprint;
    private final LocalDateTime firstTriggeredAt;
    private final LocalDateTime lastTriggeredAt;
    private final int triggerCount;
    private final LocalDateTime resolvedAt;
    private final String resolvedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    // ── 关联资产摘要 ────────────────────────────────
    @Schema(description = "关联资产 ID，若资产已删除则为 null")
    private final Long assetId;

    @Schema(description = "资产名称；资产存在时取当前值，资产已删除时取创建快照")
    private final String assetName;

    @Schema(description = "资产 IP；资产存在时取当前值，资产已删除时取创建快照")
    private final String assetIp;

    @Schema(description = "资产类型（创建时快照），用于资产已删除时展示历史类型")
    private final String assetType;

    @Schema(description = "true 表示该告警曾关联某资产但该资产已被删除（当前展示为快照数据）")
    private final boolean assetDeleted;

    // ── 关联资源跳转引用 ───────────────────────────
    @Schema(description = "关联资源引用，供前端跳转到资产/采集任务/发现结果/POC 执行记录详情页")
    private final LinkedResourcesResponse linkedResources;

    // ── POC 执行记录回链 ────────────────────────────
    @Schema(description = "触发本告警的 POC 执行记录列表，按触发时间倒序；可用 executionId 跳转到 /api/v1/poc-executions/{id}")
    private final List<RelatedExecutionEntry> relatedExecutions;

    // ── 证据与时间线 ────────────────────────────────
    private final List<AlertEvidenceResponse> evidences;
    private final List<AlertOperationResponse> operations;

    public AlertDetailResponse(Alert alert, Asset asset,
                               List<AlertEvidence> evidences,
                               List<AlertOperation> operations,
                               List<RelatedExecutionEntry> relatedExecutions) {
        this.id               = alert.getId();
        this.title            = alert.getTitle();
        this.sourceType       = alert.getSourceType();
        this.severity         = alert.getSeverity();
        this.status           = alert.getStatus();
        this.summary          = alert.getSummary();
        this.ruleCode         = alert.getRuleCode();
        this.ruleName         = alert.getRuleName();
        this.fingerprint      = alert.getFingerprint();
        this.firstTriggeredAt = alert.getFirstTriggeredAt();
        this.lastTriggeredAt  = alert.getLastTriggeredAt();
        this.triggerCount     = alert.getTriggerCount();
        this.resolvedAt       = alert.getResolvedAt();
        this.resolvedBy       = alert.getResolvedBy();
        this.createdAt        = alert.getCreatedAt();
        this.updatedAt        = alert.getUpdatedAt();

        this.assetId = alert.getAssetId();

        // Prefer current asset data; fall back to snapshot when asset was deleted
        this.assetName = asset != null ? asset.getName()  : alert.getAssetNameSnapshot();
        this.assetIp   = asset != null ? asset.getIp()    : alert.getAssetIpSnapshot();
        this.assetType = asset != null
                ? (asset.getType() != null ? asset.getType().name() : null)
                : alert.getAssetTypeSnapshot();

        this.assetDeleted = alert.getAssetId() == null && alert.getAssetNameSnapshot() != null;

        this.linkedResources   = new LinkedResourcesResponse(alert, asset, evidences);
        this.relatedExecutions = relatedExecutions;
        this.evidences  = evidences.stream().map(AlertEvidenceResponse::new).toList();
        this.operations = operations.stream().map(AlertOperationResponse::new).toList();
    }
}
