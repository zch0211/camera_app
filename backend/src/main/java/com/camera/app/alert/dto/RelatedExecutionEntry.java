package com.camera.app.alert.dto;

import com.camera.app.poc.entity.PocExecutionLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "关联 POC 执行记录摘要，供前端跳转到执行历史详情页")
public class RelatedExecutionEntry {

    @Schema(description = "执行记录 ID（即 poc_execution_logs.id），用于构造 /api/v1/poc-executions/{id} 跳转链接")
    private final Long executionId;

    @Schema(description = "所属 POC ID")
    private final Long pocId;

    @Schema(description = "执行动作 Key: CHECK_VULN / FETCH_SNAPSHOT / EXEC_COMMAND / DUMP_CREDS / LIST_USERS / DOWNLOAD_CONFIG")
    private final String actionKey;

    @Schema(description = "执行动作中文标签")
    private final String actionLabel;

    @Schema(description = "输出类型: BOOLEAN_TEXT / TEXT / JSON / IMAGE / FILE / MIXED")
    private final String outputType;

    @Schema(description = "本次执行是否成功（exit code 0）")
    private final Boolean success;

    @Schema(description = "执行目标（IP 或 URL）")
    private final String usedTarget;

    @Schema(description = "实际使用端口")
    private final Integer finalPort;

    @Schema(description = "开始执行时间")
    private final LocalDateTime startedAt;

    @Schema(description = "执行完成时间")
    private final LocalDateTime finishedAt;

    @Schema(description = "执行耗时（毫秒）")
    private final Long durationMs;

    @Schema(description = "该证据写入时间，即告警被此次执行触发或聚合更新的时间")
    private final LocalDateTime triggeredAt;

    @Schema(description = "证据描述（描述该次执行命中了什么）")
    private final String evidenceDescription;

    /** Full constructor when PocExecutionLog is available. */
    public RelatedExecutionEntry(PocExecutionLog log, LocalDateTime triggeredAt, String evidenceDescription) {
        this.executionId        = log.getId();
        this.pocId              = log.getPocId();
        this.actionKey          = log.getActionKey();
        this.actionLabel        = resolveLabel(log.getActionKey());
        this.outputType         = log.getOutputType();
        this.success            = log.getSuccess();
        this.usedTarget         = log.getUsedTarget();
        this.finalPort          = log.getFinalPort();
        this.startedAt          = log.getStartedAt();
        this.finishedAt         = log.getFinishedAt();
        this.durationMs         = log.getDurationMs();
        this.triggeredAt        = triggeredAt;
        this.evidenceDescription = evidenceDescription;
    }

    /** Fallback constructor used when execution log record cannot be loaded. */
    public RelatedExecutionEntry(Long executionId, LocalDateTime triggeredAt, String evidenceDescription) {
        this.executionId        = executionId;
        this.pocId              = null;
        this.actionKey          = null;
        this.actionLabel        = null;
        this.outputType         = null;
        this.success            = null;
        this.usedTarget         = null;
        this.finalPort          = null;
        this.startedAt          = null;
        this.finishedAt         = null;
        this.durationMs         = null;
        this.triggeredAt        = triggeredAt;
        this.evidenceDescription = evidenceDescription;
    }

    private static String resolveLabel(String actionKey) {
        if (actionKey == null) return null;
        return switch (actionKey) {
            case "CHECK_VULN"      -> "漏洞验证";
            case "EXEC_COMMAND"    -> "命令执行";
            case "FETCH_SNAPSHOT"  -> "获取快照";
            case "LIST_USERS"      -> "获取用户列表";
            case "DOWNLOAD_CONFIG" -> "下载配置";
            case "DUMP_CREDS"      -> "读取凭证";
            default                -> actionKey;
        };
    }
}
