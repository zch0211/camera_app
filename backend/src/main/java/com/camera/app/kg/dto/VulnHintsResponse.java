package com.camera.app.kg.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VulnHintsResponse {

    /** MySQL 资产 ID */
    private Long assetId;

    /** 是否在图谱中命中匹配产品节点 */
    private boolean matched;

    /**
     * 潜在漏洞提示列表。
     * 注意：本接口为图谱推理结果，仅表示"图谱中存在可能关联的漏洞"，不代表该资产已确认受影响。
     */
    private List<VulnHintItem> vulnerabilityHints;

    /** 综合摘要（说明命中数量、置信度概况） */
    private String summary;
}
