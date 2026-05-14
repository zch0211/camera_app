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
@Schema(description = "POC 执行模板，前端据此动态渲染执行表单")
public class PocExecutionSchema {

    @Schema(description = "POC ID")
    private Long pocId;

    @Schema(description = "脚本语言")
    private String language;

    @Schema(description = "是否可执行")
    private boolean executable;

    @Schema(description = "不可执行时的原因说明")
    private String reason;

    @Schema(description = "支持的执行模式：CHECK=安全检测，EXPLOIT=漏洞利用（高风险）")
    private List<ExecutionMode> modes;

    @Schema(description = "默认执行模式")
    private ExecutionMode defaultMode;

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

    @Schema(description = "当前默认模式是否为高风险（EXPLOIT 模式时为 true）")
    private boolean highRisk;

    @Schema(description = "模板版本号，用于后续演进兼容")
    private int schemaVersion;

    @Schema(description = """
            各执行模式的参数字段定义。
            key = 模式名（CHECK / EXPLOIT），value = 该模式需要的 params 字段列表。
            CHECK → 空列表（无额外参数）；
            EXPLOIT → [{ name:"cmd", type:"text", required:true }]（需要 params.cmd）
            """)
    private Map<String, List<ParamField>> paramSchemaByMode;
}
