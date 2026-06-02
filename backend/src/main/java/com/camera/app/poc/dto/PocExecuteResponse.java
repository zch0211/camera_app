package com.camera.app.poc.dto;

import com.camera.app.poc.entity.ExecutionMode;
import com.camera.app.poc.entity.TargetStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@Schema(description = "POC 执行结果（v2：含 actionKey / outputType / artifacts）")
public class PocExecuteResponse {

    private Long pocId;

    @Schema(description = "执行记录 ID，executed=true 时存在；保存失败时为 null")
    private Long executionId;

    @Schema(description = "是否实际启动了子进程（false 表示因校验失败而未执行）")
    private boolean executed;

    @Schema(description = "执行是否成功（exitCode == 0）；executed=false 时为 null")
    private Boolean success;

    @Schema(description = "进程退出码；超时/未执行时为 null")
    private Integer exitCode;

    // ─── v2 动作驱动字段 ──────────────────────────────────────────────────────

    @Schema(description = "本次执行的动作 key，如 CHECK_VULN / EXEC_COMMAND / FETCH_SNAPSHOT 等")
    private String actionKey;

    @Schema(description = "本次执行的动作显示名称，如 漏洞检测 / 执行命令")
    private String actionLabel;

    @Schema(description = "本次输出类型：BOOLEAN_TEXT / TEXT / JSON / IMAGE / FILE / MIXED")
    private String outputType;

    @Schema(description = "结果附件列表（IMAGE / FILE 类动作时填充，TEXT 类为空列表）")
    private List<ArtifactInfo> artifacts;

    @Schema(description = "结构化解析输出（outputType=JSON 时填充，其余为 null）")
    private Object parsedOutput;

    // ─── 文本输出 ─────────────────────────────────────────────────────────────

    private String stdout;
    private String stderr;

    @Schema(description = "stdout 或 stderr 任一被截断时为 true")
    private boolean truncated;

    // ─── 时序 ─────────────────────────────────────────────────────────────────

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    // ─── 执行上下文 ───────────────────────────────────────────────────────────

    @Schema(description = "【兼容 v1】本次执行模式：CHECK / EXPLOIT")
    private ExecutionMode mode;

    @Schema(description = "本次目标策略：EXPLICIT_PORT / RECOMMENDED_PORT_SCAN")
    private TargetStrategy targetStrategy;

    @Schema(description = "本次实际使用的端口，无端口注入时为 null")
    private Integer finalPort;

    @Schema(description = "本次目标字符串，如 http://192.168.1.100:8080 或裸 IP；无资产关联时为 null")
    private String usedTarget;

    @Schema(description = "未执行或超时时的说明信息")
    private String message;
}
