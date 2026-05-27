package com.camera.app.discovery.dto;

import com.camera.app.discovery.entity.DiscoveryTask;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DiscoveryTaskResponse {

    private final Long id;
    private final String taskType;
    private final String status;
    private final String preset;
    private final String targetScope;
    private final int timeoutMillis;
    private final int sniffDurationSec;
    private final String networkInterface;
    private final String summary;
    private final String errorMessage;
    private final int discoveredCount;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public DiscoveryTaskResponse(DiscoveryTask t) {
        this.id = t.getId();
        this.taskType = t.getTaskType().name();
        this.status = t.getStatus().name();
        this.preset = t.getPreset().name();
        this.targetScope = t.getTargetScope();
        this.timeoutMillis = t.getTimeoutMillis();
        this.sniffDurationSec = t.getSniffDurationSec();
        this.networkInterface = t.getNetworkInterface();
        this.summary = t.getSummary();
        this.errorMessage = t.getErrorMessage();
        this.discoveredCount = t.getDiscoveredCount();
        this.startedAt = t.getStartedAt();
        this.finishedAt = t.getFinishedAt();
        this.createdAt = t.getCreatedAt();
        this.updatedAt = t.getUpdatedAt();
    }
}
