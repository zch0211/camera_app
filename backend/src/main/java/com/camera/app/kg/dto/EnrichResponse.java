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
public class EnrichResponse {

    /** MySQL 资产 ID */
    private Long assetId;

    /** 是否在图谱中命中匹配节点 */
    private boolean matched;

    /** 图谱推断的厂商名称 */
    private String inferredBrand;

    /** 图谱推断的产品名称 */
    private String inferredProduct;

    /** 图谱推断的固件版本 */
    private String inferredFirmware;

    /** 图谱中发现的开放端口列表 */
    private List<String> inferredPorts;

    /** 图谱中发现的协议列表 */
    private List<String> inferredProtocols;

    /** 匹配到的相关节点摘要（可读文本列表） */
    private List<String> relatedNodesSummary;

    /** 推断依据路径（每条说明命中了哪个节点/关系） */
    private List<String> evidencePaths;

    /**
     * 置信度等级：HIGH（品牌+型号均命中）/ MEDIUM（仅型号命中）/ LOW（模糊匹配或仅品牌命中）
     */
    private String confidence;
}
