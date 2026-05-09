package com.camera.app.kg.service;

import com.camera.app.kg.dto.EnrichResponse;
import com.camera.app.kg.dto.GraphResponse;
import com.camera.app.kg.dto.VulnHintsResponse;

public interface CameraKgService {

    /**
     * 根据 MySQL 资产的 brand/model 查询图谱，返回补全建议。
     * 只读，不修改资产数据。
     */
    EnrichResponse enrich(Long assetId);

    /**
     * 根据 MySQL 资产的 brand/model 查询图谱中可能关联的漏洞，返回潜在风险提示。
     * 结果为图谱推理，不代表资产已确认受影响。
     */
    VulnHintsResponse vulnHints(Long assetId);

    /**
     * 返回与该资产相关的一跳子图（节点 + 边），供前端图谱可视化消费。
     */
    GraphResponse graph(Long assetId);
}
