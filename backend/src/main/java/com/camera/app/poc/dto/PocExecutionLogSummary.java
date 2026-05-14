package com.camera.app.poc.dto;

import com.camera.app.poc.entity.PocExecutionLog;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PocExecutionLogSummary {

    private final Long id;
    private final Long pocId;
    private final Long assetId;
    private final String executedBy;
    private final Boolean success;
    private final Integer exitCode;
    private final boolean truncated;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Long durationMs;
    private final LocalDateTime createdAt;

    public PocExecutionLogSummary(PocExecutionLog log) {
        this.id = log.getId();
        this.pocId = log.getPocId();
        this.assetId = log.getAssetId();
        this.executedBy = log.getExecutedBy();
        this.success = log.getSuccess();
        this.exitCode = log.getExitCode();
        this.truncated = log.isTruncated();
        this.startedAt = log.getStartedAt();
        this.finishedAt = log.getFinishedAt();
        this.durationMs = log.getDurationMs();
        this.createdAt = log.getCreatedAt();
    }
}
