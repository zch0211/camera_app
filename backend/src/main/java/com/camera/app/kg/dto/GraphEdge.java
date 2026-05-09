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
public class GraphEdge {

    /** 源节点 ID（对应 GraphNode.id） */
    private String source;

    /** 目标节点 ID（对应 GraphNode.id） */
    private String target;

    /** 关系类型（如 具备、开放、影响、运行 等） */
    private String type;
}
