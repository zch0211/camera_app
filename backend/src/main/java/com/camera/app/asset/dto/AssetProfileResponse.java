package com.camera.app.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "设备画像聚合视图")
public class AssetProfileResponse {

    @Schema(description = "基础信息（现有资产字段）")
    private final AssetResponse basicInfo;

    @Schema(description = "技术特征，若尚未录入则为 null")
    private final TechnicalProfileResponse technicalFeatures;

    @Schema(description = "后端计算的缺失字段列表（字段名数组）")
    private final List<String> missingFields;

    @Schema(description = "候选推断结果列表，按置信度降序")
    private final List<InferenceCandidateResponse> inferenceCandidates;

    @Schema(description = "证据来源列表，按采集时间降序")
    private final List<EvidenceResponse> evidences;

    @Schema(description = "端口/服务识别结果列表，按端口升序；每条记录对应一个端口的最新探测结果")
    private final List<ServiceFingerprintResponse> serviceFingerprints;

    @Schema(description = "知识图谱增强占位（后续版本填充）")
    private final KnowledgeEnhancementPlaceholder knowledgeEnhancement;

    @Getter
    @AllArgsConstructor
    @Schema(description = "知识图谱增强占位结构")
    public static class KnowledgeEnhancementPlaceholder {
        @Schema(description = "是否已关联知识图谱，当前版本固定为 false")
        private final boolean available;
        @Schema(description = "说明")
        private final String note;
    }
}
