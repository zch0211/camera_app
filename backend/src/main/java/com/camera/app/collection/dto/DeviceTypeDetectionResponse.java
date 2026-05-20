package com.camera.app.collection.dto;

import com.camera.app.asset.entity.AssetInferenceCandidate;
import com.camera.app.collection.rules.CategoryDetectionResult;
import com.camera.app.collection.rules.DeviceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Schema(description = "设备类别推断结果（由规则引擎自动生成）")
public class DeviceTypeDetectionResponse {

    @Schema(description = "推断的设备类别：IPC / NVR / ROUTER / UNKNOWN")
    private final DeviceCategory category;

    @Schema(description = "置信度 0~1")
    private final BigDecimal confidence;

    @Schema(description = "规则推断依据说明")
    private final String reason;

    @Schema(description = "命中的具体事实列表")
    private final List<String> supportingFacts;

    public DeviceTypeDetectionResponse(CategoryDetectionResult r) {
        this.category = r.getCategory();
        this.confidence = r.getConfidence();
        this.reason = r.getReason();
        this.supportingFacts = r.getSupportingFacts();
    }

    public DeviceTypeDetectionResponse(AssetInferenceCandidate c) {
        DeviceCategory parsed;
        try {
            parsed = DeviceCategory.valueOf(c.getCandidateValue());
        } catch (IllegalArgumentException e) {
            parsed = DeviceCategory.UNKNOWN;
        }
        this.category = parsed;
        this.confidence = c.getConfidence();
        this.reason = c.getReason();
        this.supportingFacts = List.of();
    }
}
