package com.camera.app.poc.dto;

import com.camera.app.poc.entity.ExecutionMode;
import com.camera.app.poc.entity.PocExecutionLog;
import com.camera.app.poc.entity.TargetStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class PocExecutionLogResponse {

    private final Long id;
    private final Long pocId;
    private final Long assetId;
    private final String executedBy;
    private final ExecutionMode mode;
    private final String actionKey;
    private final String outputType;

    @Schema(description = "附件列表（IMAGE/FILE 类动作时有值，含 downloadUrl / previewUrl）")
    private final List<ArtifactInfo> artifacts;

    @Schema(description = "原始附件摘要 JSON（与 artifacts 数据相同，保留供调试）")
    private final String artifactSummary;

    private final TargetStrategy targetStrategy;
    private final Integer finalPort;
    private final String usedTarget;
    private final Boolean success;
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean truncated;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Long durationMs;
    private final LocalDateTime createdAt;

    public PocExecutionLogResponse(PocExecutionLog log) {
        this(log, List.of());
    }

    public PocExecutionLogResponse(PocExecutionLog log, List<ArtifactInfo> artifacts) {
        this.id = log.getId();
        this.pocId = log.getPocId();
        this.assetId = log.getAssetId();
        this.executedBy = log.getExecutedBy();
        this.mode = log.getMode();
        this.actionKey = log.getActionKey();
        this.outputType = log.getOutputType();
        this.artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
        this.artifactSummary = log.getArtifactSummary();
        this.targetStrategy = log.getTargetStrategy();
        this.finalPort = log.getFinalPort();
        this.usedTarget = log.getUsedTarget();
        this.success = log.getSuccess();
        this.exitCode = log.getExitCode();
        this.stdout = log.getStdout();
        this.stderr = log.getStderr();
        this.truncated = log.isTruncated();
        this.startedAt = log.getStartedAt();
        this.finishedAt = log.getFinishedAt();
        this.durationMs = log.getDurationMs();
        this.createdAt = log.getCreatedAt();
    }
}
