package com.camera.app.alert.dto;

import com.camera.app.alert.entity.AlertEvidence;
import com.camera.app.alert.entity.AlertEvidenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Schema(description = "告警证据")
public class AlertEvidenceResponse {

    private final Long id;
    private final AlertEvidenceType evidenceType;
    private final Long refId;
    private final String fieldName;
    private final String fieldValue;
    private final String description;
    private final BigDecimal confidence;
    private final LocalDateTime createdAt;

    public AlertEvidenceResponse(AlertEvidence e) {
        this.id           = e.getId();
        this.evidenceType = e.getEvidenceType();
        this.refId        = e.getRefId();
        this.fieldName    = e.getFieldName();
        this.fieldValue   = e.getFieldValue();
        this.description  = e.getDescription();
        this.confidence   = e.getConfidence();
        this.createdAt    = e.getCreatedAt();
    }
}
