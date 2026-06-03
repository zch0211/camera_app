package com.camera.app.alert.dto;

import com.camera.app.alert.entity.AlertOperation;
import com.camera.app.alert.entity.AlertOperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "告警操作时间线记录")
public class AlertOperationResponse {

    private final Long id;
    private final AlertOperationType operationType;
    private final String operatorUsername;
    private final String comment;
    private final LocalDateTime createdAt;

    public AlertOperationResponse(AlertOperation op) {
        this.id                = op.getId();
        this.operationType     = op.getOperationType();
        this.operatorUsername  = op.getOperatorUsername();
        this.comment           = op.getComment();
        this.createdAt         = op.getCreatedAt();
    }
}
