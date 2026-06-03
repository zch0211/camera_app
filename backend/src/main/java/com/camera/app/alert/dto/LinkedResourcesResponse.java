package com.camera.app.alert.dto;

import com.camera.app.alert.entity.Alert;
import com.camera.app.alert.entity.AlertEvidence;
import com.camera.app.alert.entity.AlertEvidenceType;
import com.camera.app.asset.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@Schema(description = "告警关联资源引用，供前端跳转到资产/采集/发现/执行记录等详情页")
public class LinkedResourcesResponse {

    @Schema(description = "关联资产 ID（null 表示无关联或已删除）")
    private final Long assetId;

    @Schema(description = "资产名称（当前值或快照）")
    private final String assetName;

    @Schema(description = "资产 IP（当前值或快照）")
    private final String assetIp;

    @Schema(description = "证据引用列表，每条包含类型和原始对象 ID，可用于前端构造跳转链接")
    private final List<EvidenceRef> evidenceRefs;

    public LinkedResourcesResponse(Alert alert, Asset asset, List<AlertEvidence> evidences) {
        this.assetId   = alert.getAssetId();
        this.assetName = asset != null ? asset.getName() : alert.getAssetNameSnapshot();
        this.assetIp   = asset != null ? asset.getIp()   : alert.getAssetIpSnapshot();
        this.evidenceRefs = evidences.stream()
                .filter(e -> e.getRefId() != null)
                .map(e -> new EvidenceRef(e.getEvidenceType(), e.getRefId(), e.getDescription()))
                .toList();
    }

    @Getter
    @RequiredArgsConstructor
    @Schema(description = "单条证据引用")
    public static class EvidenceRef {
        @Schema(description = "证据类型: POC_EXECUTION / COLLECTION_RESULT / DISCOVERY_RESULT / INFERENCE_CANDIDATE / TECHNICAL_PROFILE / TEXT_NOTE")
        private final AlertEvidenceType evidenceType;

        @Schema(description = "关联对象 ID，对应该类型的主键（如 executionId / taskId / resultId 等）")
        private final Long refId;

        @Schema(description = "证据描述")
        private final String description;
    }
}
