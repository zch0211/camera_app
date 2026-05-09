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
public class GraphResponse {

    /** 子图节点列表（去重） */
    private List<GraphNode> nodes;

    /** 子图边列表 */
    private List<GraphEdge> edges;
}
