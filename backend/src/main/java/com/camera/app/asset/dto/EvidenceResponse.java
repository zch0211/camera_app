package com.camera.app.asset.dto;

import com.camera.app.asset.entity.AssetEvidence;
import com.camera.app.asset.entity.EvidenceSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Schema(description = "证据来源记录")
public class EvidenceResponse {

    @Schema(description = "记录 ID")
    private final Long id;

    @Schema(description = "资产 ID")
    private final Long assetId;

    @Schema(description = "字段名")
    private final String fieldName;

    @Schema(description = "字段值")
    private final String fieldValue;

    @Schema(description = "来源类型: MANUAL / SCAN / SNIFF / KG / MODEL / IMPORT")
    private final EvidenceSourceType sourceType;

    @Schema(description = "原始证据内容")
    private final String rawEvidence;

    @Schema(description = "置信度 0~1")
    private final BigDecimal confidence;

    @Schema(description = "采集时间")
    private final LocalDateTime collectedAt;

    @Schema(description = "备注")
    private final String note;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public EvidenceResponse(AssetEvidence e) {
        this.id = e.getId();
        this.assetId = e.getAssetId();
        this.fieldName = e.getFieldName();
        this.fieldValue = e.getFieldValue();
        this.sourceType = e.getSourceType();
        this.rawEvidence = e.getRawEvidence();
        this.confidence = e.getConfidence();
        this.collectedAt = e.getCollectedAt();
        this.note = e.getNote();
        this.createdAt = e.getCreatedAt();
        this.updatedAt = e.getUpdatedAt();
    }
}
