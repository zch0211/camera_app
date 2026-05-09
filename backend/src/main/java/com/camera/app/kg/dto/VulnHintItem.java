package com.camera.app.kg.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VulnHintItem {

    /** 漏洞名称或 CVE 编号（来自图谱节点） */
    private String vulnName;

    /** 严重程度（图谱中若有该属性则填入，如 HIGH/MEDIUM/LOW 或 CVSS 分值） */
    private String severity;

    /** 推断原因（描述如何关联到该漏洞） */
    private String reason;

    /** 从产品节点到漏洞节点的图谱路径摘要 */
    private String evidencePath;

    /**
     * 置信度：HIGH（直接相邻）/ MEDIUM（2 跳）/ LOW（3 跳及以上）
     */
    private String confidence;
}
