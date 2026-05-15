package com.camera.app.asset.dto;

import com.camera.app.asset.entity.EvidenceSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "证据来源创建/更新请求")
public class EvidenceRequest {

    @NotBlank
    @Schema(description = "字段名，如 brand / model / firmwareVersion", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldName;

    @Schema(description = "字段值")
    private String fieldValue;

    @Schema(description = "来源类型: MANUAL / SCAN / SNIFF / KG / MODEL / IMPORT，默认 MANUAL")
    private EvidenceSourceType sourceType = EvidenceSourceType.MANUAL;

    @Schema(description = "原始证据内容（如抓包片段、扫描报文）")
    private String rawEvidence;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Schema(description = "置信度 0~1，默认 1.0", example = "1.0")
    private BigDecimal confidence = BigDecimal.ONE;

    @Schema(description = "采集时间，不填则默认当前时间")
    private LocalDateTime collectedAt;

    @Schema(description = "备注")
    private String note;
}
