package com.camera.app.kg.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphNode {

    /** Neo4j 节点元素 ID（字符串，全局唯一） */
    private String id;

    /** 显示标签（节点 name 属性或首个属性值） */
    private String label;

    /** 节点类型（Neo4j 标签，如 产品、厂商、漏洞 等） */
    private String type;

    /** 节点属性（简化键值对，供前端 tooltip 展示） */
    private Map<String, Object> properties;
}
