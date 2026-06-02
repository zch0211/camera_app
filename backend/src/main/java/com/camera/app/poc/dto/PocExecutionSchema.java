package com.camera.app.poc.dto;

import com.camera.app.poc.entity.ExecutionMode;
import com.camera.app.poc.entity.TargetStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@Schema(description = "POC 执行模板 v2（动作驱动），前端据此动态渲染执行表单")
public class PocExecutionSchema {

    @Schema(description = "POC ID")
    private Long pocId;

    @Schema(description = "脚本语言")
    private String language;

    @Schema(description = "是否可执行")
    private boolean executable;

    @Schema(description = "不可执行时的原因说明")
    private String reason;

    @Schema(description = "模板版本号：2=动作驱动，1=旧模式驱动")
    private int schemaVersion;

    @Schema(description = "默认超时（秒）")
    private int defaultTimeoutSeconds;

    @Schema(description = "支持的目标策略：EXPLICIT_PORT=显式端口，RECOMMENDED_PORT_SCAN=自动扫描推荐端口")
    private List<TargetStrategy> supportedTargetStrategies;

    @Schema(description = "根据 POC protocol / targetType 推导的推荐端口列表")
    private List<Integer> recommendedPorts;

    @Schema(description = "是否支持显式指定端口")
    private boolean supportsExplicitPort;

    @Schema(description = "是否支持推荐端口自动扫描")
    private boolean supportsAutoPortSuggestion;

    // ─── v2 动作驱动字段（新前端优先消费）────────────────────────────────────

    @Schema(description = """
            动作列表（schemaVersion=2 新增）。
            每个 action 包含 key / label / category / outputType / riskLevel / defaultAction / params。
            前端应优先消费此字段渲染执行表单；旧前端仍可读取 modes / paramSchemaByMode 兼容字段。
            """)
    private List<PocAction> actions;

    // ─── v1 兼容字段（保留，旧前端仍可读取）──────────────────────────────────

    @Schema(description = "【兼容 v1】支持的执行模式列表；新前端请优先使用 actions")
    private List<ExecutionMode> modes;

    @Schema(description = "【兼容 v1】默认执行模式；新前端请优先使用 actions 中 defaultAction=true 的项")
    private ExecutionMode defaultMode;

    @Schema(description = "【兼容 v1】当前默认模式是否为高风险")
    private boolean highRisk;

    @Schema(description = "【兼容 v1】各执行模式的参数字段定义；新前端请优先使用 actions[*].params")
    private Map<String, List<ParamField>> paramSchemaByMode;
}
