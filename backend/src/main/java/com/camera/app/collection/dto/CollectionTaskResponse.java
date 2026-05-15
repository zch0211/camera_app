package com.camera.app.collection.dto;

import com.camera.app.collection.entity.AssetCollectionTask;
import com.camera.app.collection.entity.CollectionTaskStatus;
import com.camera.app.collection.entity.CollectionTaskType;
import com.camera.app.collection.entity.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "采集任务详情")
public class CollectionTaskResponse {

    @Schema(description = "任务 ID")
    private final Long id;

    @Schema(description = "资产 ID")
    private final Long assetId;

    @Schema(description = "任务类型")
    private final CollectionTaskType taskType;

    @Schema(description = "任务状态: PENDING / RUNNING / SUCCESS / FAILED")
    private final CollectionTaskStatus status;

    @Schema(description = "触发方式")
    private final TriggerType triggerType;

    @Schema(description = "开始时间")
    private final LocalDateTime startedAt;

    @Schema(description = "完成时间")
    private final LocalDateTime finishedAt;

    @Schema(description = "是否成功，任务进行中时为 null")
    private final Boolean success;

    @Schema(description = "采集摘要（成功时展示开放端口、标题等信息）")
    private final String summary;

    @Schema(description = "错误信息（失败时）")
    private final String errorMessage;

    @Schema(description = "是否已写回设备画像技术特征")
    private final boolean writebackApplied;

    @Schema(description = "原始结果条数")
    private final long resultCount;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public CollectionTaskResponse(AssetCollectionTask t, long resultCount) {
        this.id = t.getId();
        this.assetId = t.getAssetId();
        this.taskType = t.getTaskType();
        this.status = t.getStatus();
        this.triggerType = t.getTriggerType();
        this.startedAt = t.getStartedAt();
        this.finishedAt = t.getFinishedAt();
        this.success = t.getSuccess();
        this.summary = t.getSummary();
        this.errorMessage = t.getErrorMessage();
        this.writebackApplied = t.isWritebackApplied();
        this.resultCount = resultCount;
        this.createdAt = t.getCreatedAt();
        this.updatedAt = t.getUpdatedAt();
    }
}
