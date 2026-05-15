package com.camera.app.asset.dto;

import com.camera.app.asset.entity.AssetInferenceCandidate;
import com.camera.app.asset.entity.InferenceCandidateSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Schema(description = "候选推断结果")
public class InferenceCandidateResponse {

    @Schema(description = "记录 ID")
    private final Long id;

    @Schema(description = "资产 ID")
    private final Long assetId;

    @Schema(description = "推断字段名，如 brand / model / firmwareVersion")
    private final String fieldName;

    @Schema(description = "候选值")
    private final String candidateValue;

    @Schema(description = "置信度，0~1")
    private final BigDecimal confidence;

    @Schema(description = "推断依据")
    private final String reason;

    @Schema(
            description = "推断来源类型。MANUAL=人工录入；RULE=规则引擎推断；KG=知识图谱推断；MODEL=大模型推断",
            allowableValues = {"MANUAL", "RULE", "KG", "MODEL"},
            example = "MANUAL"
    )
    private final InferenceCandidateSourceType sourceType;

    @Schema(description = "是否已确认")
    private final boolean confirmed;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public InferenceCandidateResponse(AssetInferenceCandidate c) {
        this.id = c.getId();
        this.assetId = c.getAssetId();
        this.fieldName = c.getFieldName();
        this.candidateValue = c.getCandidateValue();
        this.confidence = c.getConfidence();
        this.reason = c.getReason();
        this.sourceType = c.getSourceType();
        this.confirmed = c.isConfirmed();
        this.createdAt = c.getCreatedAt();
        this.updatedAt = c.getUpdatedAt();
    }
}
