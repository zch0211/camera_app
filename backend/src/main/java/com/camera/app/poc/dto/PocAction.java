package com.camera.app.poc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "POC 可执行动作定义（动作驱动模型 v2）")
public class PocAction {

    @Schema(description = "动作唯一标识", example = "CHECK_VULN")
    private String key;

    @Schema(description = "前端显示名称", example = "漏洞检测")
    private String label;

    @Schema(description = "分类，用于前端分组：VERIFY / READ / DOWNLOAD / INTERACT")
    private String category;

    @Schema(description = "输出类型，用于结果页渲染：BOOLEAN_TEXT / TEXT / JSON / IMAGE / FILE / MIXED")
    private String outputType;

    @Schema(description = "风险级别：LOW / MEDIUM / HIGH / CRITICAL")
    private String riskLevel;

    @Schema(description = "是否为默认执行动作")
    private boolean defaultAction;

    @Schema(description = "该动作专属参数字段定义（与 paramSchemaByMode 不同，每个动作独立定义）")
    private List<ParamField> params;
}
